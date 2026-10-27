import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

/** Minimal Aseprite (.ase/.aseprite) frame exporter: 32-bit RGBA, composited over transparency. */
public final class AseToPng {
	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			throw new IllegalArgumentException("usage: AseToPng <input.ase> <out-dir> <prefix>");
		}
		Path input = Path.of(args[0]);
		Path outDir = Path.of(args[1]);
		String prefix = args[2];
		Files.createDirectories(outDir);

		byte[] bytes = Files.readAllBytes(input);
		ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if ((buf.getShort(4) & 0xFFFF) != 0xA5E0) {
			throw new IllegalArgumentException("not an Aseprite file: " + input);
		}
		int frameCount = buf.getShort(6) & 0xFFFF;
		int width = buf.getShort(8) & 0xFFFF;
		int height = buf.getShort(10) & 0xFFFF;
		int bpp = buf.getShort(12) & 0xFFFF;
		if (bpp != 32) {
			throw new IllegalArgumentException("only 32-bit RGBA is supported, got bpp=" + bpp);
		}

		int framePos = 128;
		for (int frame = 0; frame < frameCount; frame++) {
			int frameSize = buf.getInt(framePos);
			int chunkCount = buf.getInt(framePos + 12);
			int chunkPos = framePos + 16;
			int[] canvas = new int[width * height];
			for (int chunk = 0; chunk < chunkCount; chunk++) {
				int chunkSize = buf.getInt(chunkPos);
				int chunkType = buf.getShort(chunkPos + 4) & 0xFFFF;
				if (chunkType == 0x2005) {
					drawCel(buf, chunkPos + 6, chunkSize - 6, canvas, width, height);
				}
				chunkPos += chunkSize;
			}
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			image.setRGB(0, 0, width, height, canvas, 0, width);
			Path output = outDir.resolve(prefix + "_" + frame + ".png");
			ImageIO.write(image, "png", output.toFile());
			System.out.println("wrote " + output);
			framePos += frameSize;
		}
	}

	private static void drawCel(ByteBuffer buf, int celPos, int celBytes,
								int[] canvas, int spriteWidth, int spriteHeight) throws Exception {
		int x = buf.getShort(celPos + 2);
		int y = buf.getShort(celPos + 4);
		int opacity = buf.get(celPos + 6) & 0xFF;
		int celType = buf.getShort(celPos + 7) & 0xFFFF;
		int dataPos = celPos + 15;
		byte[] rgba;
		int celWidth;
		int celHeight;
		if (celType == 0) {
			celWidth = Math.max(0, spriteWidth - x);
			celHeight = Math.max(0, spriteHeight - y);
			rgba = new byte[celWidth * celHeight * 4];
			for (int i = 0; i < rgba.length; i++) {
				rgba[i] = buf.get(dataPos + i);
			}
		} else if (celType == 2) {
			// Aseprite versions differ in how many reserved bytes precede the
			// compressed image header. Locate the zlib stream instead of assuming
			// a fixed cel-header size.
			int compressedStart = -1;
			int chunkEnd = celPos + celBytes;
			for (int i = dataPos; i + 1 < chunkEnd; i++) {
				int b0 = buf.get(i) & 0xFF;
				int b1 = buf.get(i + 1) & 0xFF;
				if (b0 == 0x78 && (b1 == 0x01 || b1 == 0x5E || b1 == 0x9C || b1 == 0xDA)) {
					compressedStart = i;
					break;
				}
			}
			if (compressedStart < dataPos + 4) {
				throw new IllegalArgumentException("compressed cel has no zlib stream");
			}
			celWidth = buf.getShort(compressedStart - 4) & 0xFFFF;
			celHeight = buf.getShort(compressedStart - 2) & 0xFFFF;
			int compressedLength = celPos + celBytes - compressedStart;
			byte[] compressed = new byte[compressedLength];
			for (int i = 0; i < compressedLength; i++) {
				compressed[i] = buf.get(compressedStart + i);
			}
			try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
				rgba = in.readAllBytes();
			}
		} else {
			return; // linked cels are resolved by Aseprite itself; these assets use compressed cels
		}

		for (int py = 0; py < celHeight; py++) {
			int dy = y + py;
			if (dy < 0 || dy >= spriteHeight) {
				continue;
			}
			for (int px = 0; px < celWidth; px++) {
				int dx = x + px;
				if (dx < 0 || dx >= spriteWidth) {
					continue;
				}
				int src = (py * celWidth + px) * 4;
				if (src + 3 >= rgba.length) {
					break;
				}
				int sa = (rgba[src + 3] & 0xFF) * opacity / 255;
				if (sa == 0) {
					continue;
				}
				int sr = rgba[src] & 0xFF;
				int sg = rgba[src + 1] & 0xFF;
				int sb = rgba[src + 2] & 0xFF;
				int dst = dy * spriteWidth + dx;
				int da = (canvas[dst] >>> 24) & 0xFF;
				int dr = (canvas[dst] >>> 16) & 0xFF;
				int dg = (canvas[dst] >>> 8) & 0xFF;
				int db = canvas[dst] & 0xFF;
				int outA = sa + da * (255 - sa) / 255;
				if (outA == 0) {
					canvas[dst] = 0;
					continue;
				}
				int outR = (sr * sa + dr * da * (255 - sa) / 255) / outA;
				int outG = (sg * sa + dg * da * (255 - sa) / 255) / outA;
				int outB = (sb * sa + db * da * (255 - sa) / 255) / outA;
				canvas[dst] = (outA << 24) | (outR << 16) | (outG << 8) | outB;
			}
		}
	}

	private AseToPng() {
	}
}
