package com.tk.gun.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/** Creates the two standard 64x64 player-skin layers from an authored head cube. */
final class ArmorHeadSkinGeometry {
	private static final double[] HEAD_UV_SIZE = {8.0D, 8.0D};
	private static final double SECOND_LAYER_U_OFFSET = 32.0D;
	private static final double PIXEL = 1.0D / 16.0D;
	private static final float DRESSING_HEAD_ARMOR_VISIBLE_SECONDS = 2.0F;

	private ArmorHeadSkinGeometry() {
	}

	static GeoCube base(GeoCube source) {
		return skinCube(source, 0.01D * PIXEL, 0.0D);
	}

	static GeoCube overlay(GeoCube source) {
		return skinCube(source, 0.5D * PIXEL,
				SECOND_LAYER_U_OFFSET);
	}

	static boolean shouldHideOverlay(AbstractClientPlayerEntity player,
			boolean dressing, float animationSeconds) {
		return player != null
				&& (HelmetClientState.shouldHideHair(player)
						|| dressing && animationSeconds
								>= DRESSING_HEAD_ARMOR_VISIBLE_SECONDS);
	}

	static boolean shouldHideStationOverlay(
			AbstractClientPlayerEntity player, boolean dressing,
			float animationSeconds) {
		return player != null
				&& (HelmetClientState.isClosedState(player)
						|| HelmetClientState.shouldHideHair(player)
						|| dressing && animationSeconds
								>= DRESSING_HEAD_ARMOR_VISIBLE_SECONDS);
	}

	private static GeoCube skinCube(GeoCube source, double inflate,
			double uOffset) {
		GeoQuad[] sourceQuads = source.quads();
		GeoQuad[] quads = new GeoQuad[sourceQuads.length];
		for (int index = 0; index < sourceQuads.length; index++) {
			GeoQuad sourceQuad = sourceQuads[index];
			if (sourceQuad == null) {
				continue;
			}
			Direction direction = sourceQuad.direction();
			Vector3f normal = new Vector3f(sourceQuad.normal());
			if (normal.lengthSquared() > 1.0E-8F) {
				normal.normalize();
			}
			GeoVertex[] sourceVertices = sourceQuad.vertices();
			GeoVertex[] vertices = new GeoVertex[sourceVertices.length];
			for (int vertex = 0; vertex < sourceVertices.length; vertex++) {
				Vector3f position = new Vector3f(
						sourceVertices[vertex].position())
						.fma((float) inflate, normal);
				vertices[vertex] = new GeoVertex(position, 0.0F, 0.0F);
			}
			quads[index] = GeoQuad.build(vertices,
					uvFor(direction, uOffset),
					uvSizeFor(direction),
					64.0F, 64.0F, false, direction);
		}
		return new GeoCube(quads, source.pivot(), source.rotation(),
				source.size(), source.inflate(), source.mirror());
	}

	private static double[] uvFor(Direction direction, double uOffset) {
		return switch (direction) {
			case EAST -> new double[] {uOffset, 8.0D};
			case NORTH -> new double[] {uOffset + 8.0D, 8.0D};
			case SOUTH -> new double[] {uOffset + 24.0D, 8.0D};
			case WEST -> new double[] {uOffset + 16.0D, 8.0D};
			case UP -> new double[] {uOffset + 8.0D, 0.0D};
			case DOWN -> new double[] {uOffset + 16.0D, 8.0D};
		};
	}

	private static double[] uvSizeFor(Direction direction) {
		return direction == Direction.DOWN
				? new double[] {8.0D, -8.0D}
				: HEAD_UV_SIZE;
	}
}
