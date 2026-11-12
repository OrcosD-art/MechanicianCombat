import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Inflater;

/** Minimal Aseprite frame exporter for the authored close-combat particles. */
public final class ExportAsepriteFrames {
	private ExportAsepriteFrames() {
	}

	public static void main(String[] args) throws Exception {
		Path input = Path.of(args[0]);
		Path output = Path.of(args[1]);
		String prefix = args[2];
		byte[] bytes = Files.readAllBytes(input);
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if ((buffer.getShort(4) & 0xFFFF) != 0xA5E0) {
			throw new IOException("Not an Aseprite file");
		}
		int frameCount = buffer.getShort(6) & 0xFFFF;
		int width = buffer.getShort(8) & 0xFFFF;
		int height = buffer.getShort(10) & 0xFFFF;
		int depth = buffer.getShort(12) & 0xFFFF;
		if (depth != 32) {
			throw new IOException("Only RGB Aseprite files are supported");
		}
		Files.createDirectories(output);
		int offset = 128;
		for (int frame = 0; frame < frameCount; frame++) {
			int frameStart = offset;
			int frameBytes = buffer.getInt(offset);
			if ((buffer.getShort(offset + 4) & 0xFFFF) != 0xF1FA) {
				throw new IOException("Bad frame at " + offset);
			}
			int chunks = buffer.getInt(offset + 12);
			offset += 16;
			BufferedImage image = new BufferedImage(
					width, height, BufferedImage.TYPE_INT_ARGB);
			for (int chunk = 0; chunk < chunks; chunk++) {
				int chunkStart = offset;
				int chunkBytes = buffer.getInt(offset);
				int chunkType = buffer.getShort(offset + 4) & 0xFFFF;
				System.out.println("frame=" + frame + " chunk=" + chunk
						+ " type=0x" + Integer.toHexString(chunkType)
						+ " bytes=" + chunkBytes);
				offset += 6;
				if (chunkType == 0x2005) {
					int celX = buffer.getShort(offset + 2);
					int celY = buffer.getShort(offset + 4);
					int opacity = buffer.get(offset + 6) & 0xFF;
					int celType = buffer.getShort(offset + 7) & 0xFFFF;
					System.out.println(" cel layer=" +
							(buffer.getShort(offset) & 0xFFFF)
							+ " type=" + celType);
					System.out.print(" cel header:");
					for (int debug = 0; debug < 20; debug++) {
						System.out.printf(" %02x", buffer.get(offset + debug) & 0xFF);
					}
					System.out.println();
					if (celType == 0 || celType == 2) {
						int celWidth = buffer.getShort(offset + 16) & 0xFFFF;
						int celHeight = buffer.getShort(offset + 18) & 0xFFFF;
						int pixelBytes = celWidth * celHeight * 4;
						int dataOffset = offset + 20;
						byte[] pixels;
						if (celType == 0) {
							pixels = new byte[pixelBytes];
							System.arraycopy(bytes, dataOffset,
									pixels, 0, pixelBytes);
						} else {
							pixels = inflate(bytes, dataOffset,
									chunkStart + chunkBytes - dataOffset,
									pixelBytes);
						}
						if (frame == 0) {
							System.out.print(" first pixels:");
							for (int index = 0; index < 16; index++) {
								System.out.printf(" %02x",
										pixels[index] & 0xFF);
							}
							System.out.println();
						}
						composite(image, pixels, celWidth, celHeight,
								celX, celY, opacity);
					}
				}
				offset = chunkStart + chunkBytes;
			}
			ImageIO.write(image, "png",
					output.resolve(prefix + "_" + frame + ".png").toFile());
			offset = frameStart + frameBytes;
		}
	}

	private static byte[] inflate(byte[] source, int offset, int length,
			int expected) throws Exception {
		Inflater inflater = new Inflater();
		inflater.setInput(source, offset, length);
		ByteArrayOutputStream output = new ByteArrayOutputStream(expected);
		byte[] chunk = new byte[4096];
		while (!inflater.finished() && output.size() < expected) {
			int read = inflater.inflate(chunk);
			if (read == 0 && inflater.needsInput()) {
				break;
			}
			output.write(chunk, 0, read);
		}
		inflater.end();
		byte[] result = output.toByteArray();
		if (result.length < expected) {
			throw new IOException("Inflated cel is too short");
		}
		return result;
	}

	private static void composite(BufferedImage image, byte[] pixels,
			int width, int height, int offsetX, int offsetY, int opacity) {
		for (int y = 0; y < height; y++) {
			int targetY = y + offsetY;
			if (targetY < 0 || targetY >= image.getHeight()) {
				continue;
			}
			for (int x = 0; x < width; x++) {
				int targetX = x + offsetX;
				if (targetX < 0 || targetX >= image.getWidth()) {
					continue;
				}
				int sourceIndex = (y * width + x) * 4;
				int r = pixels[sourceIndex] & 0xFF;
				int g = pixels[sourceIndex + 1] & 0xFF;
				int b = pixels[sourceIndex + 2] & 0xFF;
				int a = (pixels[sourceIndex + 3] & 0xFF) * opacity / 255;
				if (a == 0) {
					continue;
				}
				int rgb = (a << 24) | (r << 16) | (g << 8) | b;
				image.setRGB(targetX, targetY, rgb);
			}
		}
	}
}
