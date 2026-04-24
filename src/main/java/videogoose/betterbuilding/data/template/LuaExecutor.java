package videogoose.betterbuilding.data.template;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.Map;
import java.util.Random;

/**
 * Sandboxed LuaJ runtime that exposes building primitives as Lua functions.
 * The LLM generates Lua code that calls these functions to build a template.
 */
public class LuaExecutor {

	private final TemplateMetaData template;
	private final int[] dims;
	private final Map<String, Short> palette;
	private int totalOps;
	private String templateName;

	public LuaExecutor(TemplateMetaData template, int[] dims, Map<String, Short> palette) {
		this.template = template;
		this.dims = dims;
		this.palette = palette;
		this.totalOps = 0;
		this.templateName = null;
	}

	public int getTotalOps() {
		return totalOps;
	}

	public String getTemplateName() {
		return templateName;
	}

	/**
	 * Execute Lua code in a sandboxed environment with building primitives.
	 * @param luaCode The Lua source code to execute
	 * @throws LuaError if the code has syntax or runtime errors
	 */
	public void execute(String luaCode) throws LuaError {
		Globals globals = JsePlatform.standardGlobals();
		sandbox(globals);
		registerPrimitives(globals);
		globals.load(luaCode, "build").call();
	}

	/**
	 * Remove dangerous libraries from the Lua environment.
	 */
	private void sandbox(Globals globals) {
		globals.set("io", LuaValue.NIL);
		globals.set("os", LuaValue.NIL);
		globals.set("luajava", LuaValue.NIL);
		globals.set("debug", LuaValue.NIL);
		globals.set("load", LuaValue.NIL);
		globals.set("loadfile", LuaValue.NIL);
		globals.set("dofile", LuaValue.NIL);
		globals.set("require", LuaValue.NIL);
		globals.set("package", LuaValue.NIL);
	}

	private void registerPrimitives(Globals globals) {
		// Expose dimensions
		LuaTable dimsTable = new LuaTable();
		dimsTable.set("x", dims[0]);
		dimsTable.set("y", dims[1]);
		dimsTable.set("z", dims[2]);
		globals.set("dims", dimsTable);

		// Expose named orientation constants
		globals.set("orient", buildOrientTable());

		// Expose block palette as named constants
		if(palette != null) {
			LuaTable blocksTable = new LuaTable();
			for(Map.Entry<String, Short> entry : palette.entrySet()) {
				blocksTable.set(entry.getKey(), entry.getValue().intValue());
			}
			globals.set("blocks", blocksTable);
		}

		// Building primitives
		globals.set("fill", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				byte orient = args.narg() >= 8 ? (byte) args.optint(8, 0) : 0;

				int count = 0;
				for(int x = Math.min(fx, tx); x <= Math.max(fx, tx); x++)
					for(int y = Math.min(fy, ty); y <= Math.max(fy, ty); y++)
						for(int z = Math.min(fz, tz); z <= Math.max(fz, tz); z++)
							if(inBounds(x, y, z)) {
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("shell", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				byte orient = args.narg() >= 9 ? (byte) args.optint(9, 0) : 0;
				int thickness = args.narg() >= 8 ? args.optint(8, 1) : 1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							boolean onShell = (x - minX < thickness) || (maxX - x < thickness) ||
									(y - minY < thickness) || (maxY - y < thickness) ||
									(z - minZ < thickness) || (maxZ - z < thickness);
							if(onShell && inBounds(x, y, z)) {
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("place", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int x = args.checkint(1), y = args.checkint(2), z = args.checkint(3);
				short type = (short) args.checkint(4);
				byte orient = args.narg() >= 5 ? (byte) args.optint(5, 0) : 0;

				if(!inBounds(x, y, z)) return LuaValue.valueOf(0);
				template.setTypeAt(x, y, z, type);
				template.setOrientationAt(x, y, z, orient);
				totalOps++;
				return LuaValue.valueOf(1);
			}
		});

		globals.set("line", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				byte orient = args.narg() >= 8 ? (byte) args.optint(8, 0) : 0;

				int dx = Math.abs(tx - fx), dy = Math.abs(ty - fy), dz = Math.abs(tz - fz);
				int sx = fx < tx ? 1 : -1, sy = fy < ty ? 1 : -1, sz = fz < tz ? 1 : -1;
				int dm = Math.max(dx, Math.max(dy, dz));
				int x = fx, y = fy, z = fz;
				int xErr = dm / 2, yErr = dm / 2, zErr = dm / 2;

				int count = 0;
				for(int i = 0; i <= dm; i++) {
					if(inBounds(x, y, z)) {
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
					xErr -= dx; if(xErr < 0) { xErr += dm; x += sx; }
					yErr -= dy; if(yErr < 0) { yErr += dm; y += sy; }
					zErr -= dz; if(zErr < 0) { zErr += dm; z += sz; }
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("clear", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);

				int count = 0;
				for(int x = Math.min(fx, tx); x <= Math.max(fx, tx); x++)
					for(int y = Math.min(fy, ty); y <= Math.max(fy, ty); y++)
						for(int z = Math.min(fz, tz); z <= Math.max(fz, tz); z++)
							if(inBounds(x, y, z)) {
								template.setTypeAt(x, y, z, (short) 0);
								template.setOrientationAt(x, y, z, (byte) 0);
								count++;
							}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("ellipsoid", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int cx = args.checkint(1), cy = args.checkint(2), cz = args.checkint(3);
				int rx = args.checkint(4), ry = args.checkint(5), rz = args.checkint(6);
				short type = (short) args.checkint(7);
				byte orient = args.narg() >= 9 ? (byte) args.optint(9, 0) : 0;
				boolean hollow = args.narg() >= 8 && args.optboolean(8, false);

				int count = 0;
				for(int x = cx - rx; x <= cx + rx; x++)
					for(int y = cy - ry; y <= cy + ry; y++)
						for(int z = cz - rz; z <= cz + rz; z++) {
							if(!inBounds(x, y, z)) continue;
							double dx = (double)(x - cx) / Math.max(rx, 1);
							double dy = (double)(y - cy) / Math.max(ry, 1);
							double dz = (double)(z - cz) / Math.max(rz, 1);
							double dist = dx * dx + dy * dy + dz * dz;
							if(dist <= 1.0) {
								if(hollow) {
									double dxIn = (double)(x - cx) / Math.max(rx - 1, 1);
									double dyIn = (double)(y - cy) / Math.max(ry - 1, 1);
									double dzIn = (double)(z - cz) / Math.max(rz - 1, 1);
									if(dxIn * dxIn + dyIn * dyIn + dzIn * dzIn <= 1.0 && rx > 1 && ry > 1 && rz > 1) continue;
								}
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("cylinder", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int x1 = args.checkint(1), y1 = args.checkint(2), z1 = args.checkint(3);
				int x2 = args.checkint(4), y2 = args.checkint(5), z2 = args.checkint(6);
				int radius = args.checkint(7);
				short type = (short) args.checkint(8);
				byte orient = args.narg() >= 10 ? (byte) args.optint(10, 0) : 0;
				boolean hollow = args.narg() >= 9 && args.optboolean(9, false);

				double lx = x2 - x1, ly = y2 - y1, lz = z2 - z1;
				double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
				if(len < 0.001) return LuaValue.valueOf(0);
				double ux = lx / len, uy = ly / len, uz = lz / len;

				int minX = Math.min(x1, x2) - radius, maxX = Math.max(x1, x2) + radius;
				int minY = Math.min(y1, y2) - radius, maxY = Math.max(y1, y2) + radius;
				int minZ = Math.min(z1, z2) - radius, maxZ = Math.max(z1, z2) + radius;

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							double px = x - x1, py = y - y1, pz = z - z1;
							double t = px * ux + py * uy + pz * uz;
							if(t < -0.5 || t > len + 0.5) continue;
							double projX = t * ux, projY = t * uy, projZ = t * uz;
							double distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY) + (pz - projZ) * (pz - projZ);
							double r = radius + 0.5;
							if(distSq <= r * r) {
								if(hollow) {
									double rInner = radius - 0.5;
									if(rInner > 0 && distSq <= rInner * rInner && t >= 0.5 && t <= len - 0.5) continue;
								}
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("cone", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int x1 = args.checkint(1), y1 = args.checkint(2), z1 = args.checkint(3);
				int x2 = args.checkint(4), y2 = args.checkint(5), z2 = args.checkint(6);
				int baseRadius = args.checkint(7);
				short type = (short) args.checkint(8);
				int tipRadius = args.narg() >= 9 ? args.optint(9, 0) : 0;
				byte orient = args.narg() >= 11 ? (byte) args.optint(11, 0) : 0;
				boolean hollow = args.narg() >= 10 && args.optboolean(10, false);

				double lx = x2 - x1, ly = y2 - y1, lz = z2 - z1;
				double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
				if(len < 0.001) return LuaValue.valueOf(0);
				double ux = lx / len, uy = ly / len, uz = lz / len;

				int maxR = Math.max(baseRadius, tipRadius);
				int minX = Math.min(x1, x2) - maxR, maxX = Math.max(x1, x2) + maxR;
				int minY = Math.min(y1, y2) - maxR, maxY = Math.max(y1, y2) + maxR;
				int minZ = Math.min(z1, z2) - maxR, maxZ = Math.max(z1, z2) + maxR;

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							double px = x - x1, py = y - y1, pz = z - z1;
							double t = px * ux + py * uy + pz * uz;
							if(t < -0.5 || t > len + 0.5) continue;
							double frac = Math.max(0, Math.min(1, t / len));
							double localRadius = baseRadius + (tipRadius - baseRadius) * frac + 0.5;
							double projX = t * ux, projY = t * uy, projZ = t * uz;
							double distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY) + (pz - projZ) * (pz - projZ);
							if(distSq <= localRadius * localRadius) {
								if(hollow) {
									double innerR = localRadius - 1.0;
									if(innerR > 0 && distSq <= innerR * innerR && t >= 0.5 && t <= len - 0.5) continue;
								}
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		// --- New shape primitives ---

		globals.set("torus", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int cx = args.checkint(1), cy = args.checkint(2), cz = args.checkint(3);
				int majorR = args.checkint(4);
				int minorR = args.checkint(5);
				short type = (short) args.checkint(6);
				String axis = args.narg() >= 7 ? args.optjstring(7, "Y").toUpperCase() : "Y";
				boolean hollow = args.narg() >= 8 && args.optboolean(8, false);
				byte orient = args.narg() >= 9 ? (byte) args.optint(9, 0) : 0;

				int bound = majorR + minorR + 1;
				int count = 0;
				for(int x = cx - bound; x <= cx + bound; x++)
					for(int y = cy - bound; y <= cy + bound; y++)
						for(int z = cz - bound; z <= cz + bound; z++) {
							if(!inBounds(x, y, z)) continue;
							double dx = x - cx, dy = y - cy, dz = z - cz;
							// Distance from the ring center circle
							double ringDist, axialDist;
							switch(axis) {
								case "X":
									ringDist = Math.sqrt(dy * dy + dz * dz) - majorR;
									axialDist = dx;
									break;
								case "Z":
									ringDist = Math.sqrt(dx * dx + dy * dy) - majorR;
									axialDist = dz;
									break;
								default: // Y
									ringDist = Math.sqrt(dx * dx + dz * dz) - majorR;
									axialDist = dy;
									break;
							}
							double dist = Math.sqrt(ringDist * ringDist + axialDist * axialDist);
							if(dist <= minorR + 0.5) {
								if(hollow && dist <= minorR - 0.5 && minorR > 1) continue;
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("pyramid", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				boolean hollow = args.narg() >= 8 && args.optboolean(8, false);
				byte orient = args.narg() >= 9 ? (byte) args.optint(9, 0) : 0;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);
				int height = maxY - minY;
				if(height == 0) height = 1;

				int count = 0;
				for(int y = minY; y <= maxY; y++) {
					double frac = (double)(y - minY) / height;
					int insetX = (int)(frac * (maxX - minX) / 2.0);
					int insetZ = (int)(frac * (maxZ - minZ) / 2.0);
					int layerMinX = minX + insetX, layerMaxX = maxX - insetX;
					int layerMinZ = minZ + insetZ, layerMaxZ = maxZ - insetZ;
					if(layerMinX > layerMaxX || layerMinZ > layerMaxZ) break;

					for(int x = layerMinX; x <= layerMaxX; x++)
						for(int z = layerMinZ; z <= layerMaxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							if(hollow && x > layerMinX && x < layerMaxX && z > layerMinZ && z < layerMaxZ
									&& y > minY && y < maxY) continue;
							template.setTypeAt(x, y, z, type);
							template.setOrientationAt(x, y, z, orient);
							count++;
						}
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("arc", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int cx = args.checkint(1), cy = args.checkint(2), cz = args.checkint(3);
				int radius = args.checkint(4);
				double startAngle = Math.toRadians(args.checkdouble(5));
				double endAngle = Math.toRadians(args.checkdouble(6));
				short type = (short) args.checkint(7);
				String axis = args.narg() >= 8 ? args.optjstring(8, "Y").toUpperCase() : "Y";
				int thickness = args.narg() >= 9 ? args.optint(9, 1) : 1;
				byte orient = args.narg() >= 10 ? (byte) args.optint(10, 0) : 0;

				// Normalize angles
				while(endAngle <= startAngle) endAngle += 2 * Math.PI;

				int count = 0;
				int bound = radius + 1;
				for(int x = cx - bound; x <= cx + bound; x++)
					for(int y = cy - bound; y <= cy + bound; y++)
						for(int z = cz - bound; z <= cz + bound; z++) {
							if(!inBounds(x, y, z)) continue;
							double dx = x - cx, dy = y - cy, dz = z - cz;
							double dist, angle;
							switch(axis) {
								case "X":
									dist = Math.sqrt(dy * dy + dz * dz);
									angle = Math.atan2(dz, dy);
									if(Math.abs(dx) > 0.5) continue;
									break;
								case "Z":
									dist = Math.sqrt(dx * dx + dy * dy);
									angle = Math.atan2(dy, dx);
									if(Math.abs(dz) > 0.5) continue;
									break;
								default: // Y
									dist = Math.sqrt(dx * dx + dz * dz);
									angle = Math.atan2(dz, dx);
									if(Math.abs(dy) > 0.5) continue;
									break;
							}
							if(dist < radius - thickness + 0.5 || dist > radius + 0.5) continue;
							// Normalize angle to match range
							while(angle < startAngle) angle += 2 * Math.PI;
							if(angle > endAngle) continue;

							template.setTypeAt(x, y, z, type);
							template.setOrientationAt(x, y, z, orient);
							count++;
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		// --- Transform primitives ---

		globals.set("copy", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				int dx = args.checkint(7), dy = args.checkint(8), dz = args.checkint(9);

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);
				int offX = dx - minX, offY = dy - minY, offZ = dz - minZ;

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							short btype = template.getTypeAt(x, y, z);
							if(btype == 0) continue;
							int nx = x + offX, ny = y + offY, nz = z + offZ;
							if(!inBounds(nx, ny, nz)) continue;
							template.setTypeAt(nx, ny, nz, btype);
							template.setOrientationAt(nx, ny, nz, template.getOrientationAt(x, y, z));
							count++;
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("replace", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short oldType = (short) args.checkint(7);
				short newType = (short) args.checkint(8);
				byte newOrient = args.narg() >= 9 ? (byte) args.optint(9, -1) : -1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							if(template.getTypeAt(x, y, z) == oldType) {
								template.setTypeAt(x, y, z, newType);
								if(newOrient >= 0) template.setOrientationAt(x, y, z, newOrient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("rotate", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				String axis = args.checkjstring(7).toUpperCase();
				int degrees = args.checkint(8);

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);
				int steps = ((degrees % 360) + 360) % 360 / 90;
				if(steps == 0) return LuaValue.valueOf(0);

				// Read source region into buffer
				int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
				short[] bufTypes = new short[sx * sy * sz];
				byte[] bufOrients = new byte[sx * sy * sz];
				for(int x = 0; x < sx; x++)
					for(int y = 0; y < sy; y++)
						for(int z = 0; z < sz; z++) {
							int idx = x + y * sx + z * sx * sy;
							if(inBounds(x + minX, y + minY, z + minZ)) {
								bufTypes[idx] = template.getTypeAt(x + minX, y + minY, z + minZ);
								bufOrients[idx] = template.getOrientationAt(x + minX, y + minY, z + minZ);
							}
						}

				// Clear source region
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++)
							if(inBounds(x, y, z)) {
								template.setTypeAt(x, y, z, (short) 0);
								template.setOrientationAt(x, y, z, (byte) 0);
							}

				// Place rotated blocks
				int count = 0;
				for(int x = 0; x < sx; x++)
					for(int y = 0; y < sy; y++)
						for(int z = 0; z < sz; z++) {
							int idx = x + y * sx + z * sx * sy;
							if(bufTypes[idx] == 0) continue;
							int nx = x, ny = y, nz = z;
							byte no = bufOrients[idx];
							for(int s = 0; s < steps; s++) {
								int tmp;
								switch(axis) {
									case "X":
										tmp = ny; ny = nz; nz = (axis.equals("X") ? sy : sz) - 1 - tmp;
										// Rotate orientations around X
										no = rotateOrientX(no);
										break;
									case "Y":
										tmp = nx; nx = nz; nz = sx - 1 - tmp;
										no = rotateOrientY(no);
										break;
									default: // Z
										tmp = nx; nx = ny; ny = sx - 1 - tmp;
										no = rotateOrientZ(no);
										break;
								}
							}
							int wx = nx + minX, wy = ny + minY, wz = nz + minZ;
							if(inBounds(wx, wy, wz)) {
								template.setTypeAt(wx, wy, wz, bufTypes[idx]);
								template.setOrientationAt(wx, wy, wz, no);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		// --- Query primitives ---

		globals.set("get_block", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int x = args.checkint(1), y = args.checkint(2), z = args.checkint(3);
				if(!inBounds(x, y, z)) return LuaValue.varargsOf(LuaValue.valueOf(0), LuaValue.valueOf(0));
				return LuaValue.varargsOf(
						LuaValue.valueOf(template.getTypeAt(x, y, z)),
						LuaValue.valueOf(template.getOrientationAt(x, y, z)));
			}
		});

		globals.set("count_blocks", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				int filterType = args.narg() >= 7 ? args.optint(7, -1) : -1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							short t = template.getTypeAt(x, y, z);
							if(t == 0) continue;
							if(filterType >= 0 && t != (short) filterType) continue;
							count++;
						}
				return LuaValue.valueOf(count);
			}
		});

		// --- Utility primitives ---

		globals.set("set_name", new OneArgFunction() {
			public LuaValue call(LuaValue arg) {
				templateName = arg.checkjstring()
						.trim()
						.replaceAll("[^a-zA-Z0-9_\\-]", "_")
						.replaceAll("_+", "_");
				return LuaValue.TRUE;
			}
		});

		globals.set("noise", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				double density = args.checkdouble(8);
				long seed = args.narg() >= 9 ? (long) args.optint(9, 0) : 0;
				byte orient = args.narg() >= 10 ? (byte) args.optint(10, 0) : 0;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				Random rng = seed != 0 ? new Random(seed) : new Random();
				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							if(rng.nextDouble() < density) {
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		// --- Advanced utility primitives ---

		globals.set("hollow", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				int thickness = args.narg() >= 7 ? args.optint(7, 1) : 1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				// For each solid block, check if it's far enough from any air/boundary
				// to be considered interior. If so, remove it.
				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							if(template.getTypeAt(x, y, z) == 0) continue;
							// Check distance to nearest air or boundary in all 6 directions
							boolean isInterior = true;
							int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
							for(int[] d : dirs) {
								boolean foundSurface = false;
								for(int step = 1; step <= thickness; step++) {
									int nx = x + d[0] * step, ny = y + d[1] * step, nz = z + d[2] * step;
									if(!inBounds(nx, ny, nz) || template.getTypeAt(nx, ny, nz) == 0) {
										foundSurface = true;
										break;
									}
								}
								if(foundSurface) {
									isInterior = false;
									break;
								}
							}
							if(isInterior) {
								template.setTypeAt(x, y, z, (short) 0);
								template.setOrientationAt(x, y, z, (byte) 0);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("gradient", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short typeA = (short) args.checkint(7);
				short typeB = (short) args.checkint(8);
				String axis = args.narg() >= 9 ? args.optjstring(9, "Z").toUpperCase() : "Z";
				byte orient = args.narg() >= 10 ? (byte) args.optint(10, 0) : 0;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							double frac;
							switch(axis) {
								case "X": frac = maxX > minX ? (double)(x - minX) / (maxX - minX) : 0; break;
								case "Y": frac = maxY > minY ? (double)(y - minY) / (maxY - minY) : 0; break;
								default:  frac = maxZ > minZ ? (double)(z - minZ) / (maxZ - minZ) : 0; break;
							}
							short type = frac < 0.5 ? typeA : typeB;
							template.setTypeAt(x, y, z, type);
							template.setOrientationAt(x, y, z, orient);
							count++;
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("scatter_surface", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				double density = args.checkdouble(8);
				long seed = args.narg() >= 9 ? (long) args.optint(9, 0) : 0;
				byte orient = args.narg() >= 10 ? (byte) args.optint(10, 0) : 0;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				Random rng = seed != 0 ? new Random(seed) : new Random();
				int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							if(template.getTypeAt(x, y, z) != 0) continue; // must be air
							// Check if adjacent to any solid block
							boolean onSurface = false;
							for(int[] d : dirs) {
								int nx = x + d[0], ny = y + d[1], nz = z + d[2];
								if(inBounds(nx, ny, nz) && template.getTypeAt(nx, ny, nz) != 0) {
									onSurface = true;
									break;
								}
							}
							if(onSurface && rng.nextDouble() < density) {
								template.setTypeAt(x, y, z, type);
								template.setOrientationAt(x, y, z, orient);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("extrude", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fz = args.checkint(2);
				int tx = args.checkint(3), tz = args.checkint(4);
				int srcY = args.checkint(5);
				int height = args.checkint(6);
				short type = (short) args.checkint(7);
				byte orient = args.narg() >= 8 ? (byte) args.optint(8, 0) : 0;
				boolean copyExisting = args.narg() >= 9 && args.optboolean(9, false);

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);
				int dir = height >= 0 ? 1 : -1;
				int absH = Math.abs(height);

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int z = minZ; z <= maxZ; z++) {
						// Check if source position has a block (or use provided type)
						short srcType;
						byte srcOrient;
						if(copyExisting && inBounds(x, srcY, z) && template.getTypeAt(x, srcY, z) != 0) {
							srcType = template.getTypeAt(x, srcY, z);
							srcOrient = template.getOrientationAt(x, srcY, z);
						} else if(copyExisting) {
							continue; // skip air positions when copying existing
						} else {
							srcType = type;
							srcOrient = orient;
						}
						for(int step = 1; step <= absH; step++) {
							int y = srcY + step * dir;
							if(!inBounds(x, y, z)) break;
							template.setTypeAt(x, y, z, srcType);
							template.setOrientationAt(x, y, z, srcOrient);
							count++;
						}
					}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("flood_fill", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int sx = args.checkint(1), sy = args.checkint(2), sz = args.checkint(3);
				short type = (short) args.checkint(4);
				byte orient = args.narg() >= 5 ? (byte) args.optint(5, 0) : 0;
				int maxBlocks = args.narg() >= 6 ? args.optint(6, 100000) : 100000;

				if(!inBounds(sx, sy, sz)) return LuaValue.valueOf(0);
				if(template.getTypeAt(sx, sy, sz) != 0) return LuaValue.valueOf(0);

				java.util.Queue<int[]> queue = new java.util.LinkedList<int[]>();
				java.util.Set<Long> visited = new java.util.HashSet<Long>();
				queue.add(new int[]{sx, sy, sz});
				visited.add(packCoord(sx, sy, sz));

				int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
				int count = 0;
				while(!queue.isEmpty() && count < maxBlocks) {
					int[] pos = queue.poll();
					int x = pos[0], y = pos[1], z = pos[2];
					template.setTypeAt(x, y, z, type);
					template.setOrientationAt(x, y, z, orient);
					count++;

					for(int[] d : dirs) {
						int nx = x + d[0], ny = y + d[1], nz = z + d[2];
						if(!inBounds(nx, ny, nz)) continue;
						if(template.getTypeAt(nx, ny, nz) != 0) continue;
						long key = packCoord(nx, ny, nz);
						if(visited.contains(key)) continue;
						visited.add(key);
						queue.add(new int[]{nx, ny, nz});
					}
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		// --- Shaping primitives ---

		globals.set("smooth", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				int iterations = args.narg() >= 7 ? args.optint(7, 1) : 1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int iter = 0; iter < iterations; iter++) {
					for(int x = minX; x <= maxX; x++)
						for(int y = minY; y <= maxY; y++)
							for(int z = minZ; z <= maxZ; z++) {
								if(!inBounds(x, y, z)) continue;
								short current = template.getTypeAt(x, y, z);
								int solidNeighbors = 0;
								int totalNeighbors = 0;
								short mostCommonType = 0;
								int mostCommonCount = 0;
								// Count 6-connected neighbors
								int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
								java.util.Map<Short, Integer> typeCounts = new java.util.HashMap<Short, Integer>();
								for(int[] off : offsets) {
									int nx = x + off[0], ny = y + off[1], nz = z + off[2];
									if(!inBounds(nx, ny, nz)) continue;
									totalNeighbors++;
									short nt = template.getTypeAt(nx, ny, nz);
									if(nt != 0) {
										solidNeighbors++;
										Integer c = typeCounts.get(nt);
										int nc = (c != null ? c : 0) + 1;
										typeCounts.put(nt, nc);
										if(nc > mostCommonCount) {
											mostCommonCount = nc;
											mostCommonType = nt;
										}
									}
								}
								if(current == 0 && solidNeighbors >= 4 && mostCommonType != 0) {
									// Fill isolated air pockets surrounded by solid blocks
									template.setTypeAt(x, y, z, mostCommonType);
									count++;
								} else if(current != 0 && solidNeighbors <= 1) {
									// Remove floating blocks with only 0-1 neighbors
									template.setTypeAt(x, y, z, (short) 0);
									template.setOrientationAt(x, y, z, (byte) 0);
									count++;
								}
							}
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("erode", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				int iterations = args.narg() >= 7 ? args.optint(7, 1) : 1;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int iter = 0; iter < iterations; iter++) {
					// Find blocks to remove (surface blocks with exposed faces)
					java.util.List<int[]> toRemove = new java.util.ArrayList<int[]>();
					for(int x = minX; x <= maxX; x++)
						for(int y = minY; y <= maxY; y++)
							for(int z = minZ; z <= maxZ; z++) {
								if(!inBounds(x, y, z)) continue;
								if(template.getTypeAt(x, y, z) == 0) continue;
								int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
								for(int[] off : offsets) {
									int nx = x + off[0], ny = y + off[1], nz = z + off[2];
									if(!inBounds(nx, ny, nz) || template.getTypeAt(nx, ny, nz) == 0) {
										toRemove.add(new int[]{x, y, z});
										break;
									}
								}
							}
					for(int[] pos : toRemove) {
						template.setTypeAt(pos[0], pos[1], pos[2], (short) 0);
						template.setOrientationAt(pos[0], pos[1], pos[2], (byte) 0);
						count++;
					}
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("dilate", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				short type = (short) args.checkint(7);
				int iterations = args.narg() >= 8 ? args.optint(8, 1) : 1;
				byte orient = args.narg() >= 9 ? (byte) args.optint(9, 0) : 0;

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				for(int iter = 0; iter < iterations; iter++) {
					java.util.List<int[]> toAdd = new java.util.ArrayList<int[]>();
					for(int x = minX; x <= maxX; x++)
						for(int y = minY; y <= maxY; y++)
							for(int z = minZ; z <= maxZ; z++) {
								if(!inBounds(x, y, z)) continue;
								if(template.getTypeAt(x, y, z) != 0) continue;
								// Check if any neighbor is solid
								int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
								for(int[] off : offsets) {
									int nx = x + off[0], ny = y + off[1], nz = z + off[2];
									if(inBounds(nx, ny, nz) && template.getTypeAt(nx, ny, nz) != 0) {
										toAdd.add(new int[]{x, y, z});
										break;
									}
								}
							}
					for(int[] pos : toAdd) {
						template.setTypeAt(pos[0], pos[1], pos[2], type);
						template.setOrientationAt(pos[0], pos[1], pos[2], orient);
						count++;
					}
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("flatten", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				int fx = args.checkint(1), fy = args.checkint(2), fz = args.checkint(3);
				int tx = args.checkint(4), ty = args.checkint(5), tz = args.checkint(6);
				String axis = args.narg() >= 7 ? args.optjstring(7, "Y").toUpperCase() : "Y";
				String mode = args.narg() >= 8 ? args.optjstring(8, "max").toLowerCase() : "max";

				int minX = Math.min(fx, tx), maxX = Math.max(fx, tx);
				int minY = Math.min(fy, ty), maxY = Math.max(fy, ty);
				int minZ = Math.min(fz, tz), maxZ = Math.max(fz, tz);

				int count = 0;
				// For each column along the axis, find the target height and fill/trim
				switch(axis) {
					case "Y":
						for(int x = minX; x <= maxX; x++)
							for(int z = minZ; z <= maxZ; z++) {
								int maxH = -1, minH = maxY + 1;
								for(int y = minY; y <= maxY; y++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										if(y > maxH) maxH = y;
										if(y < minH) minH = y;
									}
								}
								if(maxH < 0) continue;
								int target = mode.equals("min") ? minH : maxH;
								// Find the most common type in this column
								short colType = findColumnType(x, minY, maxY, z, "Y");
								for(int y = minY; y <= target; y++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) == 0) {
										template.setTypeAt(x, y, z, colType);
										count++;
									}
								}
								for(int y = target + 1; y <= maxY; y++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										template.setTypeAt(x, y, z, (short) 0);
										template.setOrientationAt(x, y, z, (byte) 0);
										count++;
									}
								}
							}
						break;
					case "X":
						for(int y = minY; y <= maxY; y++)
							for(int z = minZ; z <= maxZ; z++) {
								int maxH = -1, minH = maxX + 1;
								for(int x = minX; x <= maxX; x++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										if(x > maxH) maxH = x;
										if(x < minH) minH = x;
									}
								}
								if(maxH < 0) continue;
								int target = mode.equals("min") ? minH : maxH;
								short colType = findColumnType(minX, y, maxX, z, "X_ROW");
								for(int x = minX; x <= target; x++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) == 0) {
										template.setTypeAt(x, y, z, colType);
										count++;
									}
								}
								for(int x = target + 1; x <= maxX; x++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										template.setTypeAt(x, y, z, (short) 0);
										template.setOrientationAt(x, y, z, (byte) 0);
										count++;
									}
								}
							}
						break;
					case "Z":
						for(int x = minX; x <= maxX; x++)
							for(int y = minY; y <= maxY; y++) {
								int maxH = -1, minH = maxZ + 1;
								for(int z = minZ; z <= maxZ; z++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										if(z > maxH) maxH = z;
										if(z < minH) minH = z;
									}
								}
								if(maxH < 0) continue;
								int target = mode.equals("min") ? minH : maxH;
								short colType = findColumnType(x, y, minZ, maxZ, "Z_ROW");
								for(int z = minZ; z <= target; z++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) == 0) {
										template.setTypeAt(x, y, z, colType);
										count++;
									}
								}
								for(int z = target + 1; z <= maxZ; z++) {
									if(inBounds(x, y, z) && template.getTypeAt(x, y, z) != 0) {
										template.setTypeAt(x, y, z, (short) 0);
										template.setOrientationAt(x, y, z, (byte) 0);
										count++;
									}
								}
							}
						break;
				}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});

		globals.set("mirror", new VarArgFunction() {
			public Varargs invoke(Varargs args) {
				String axis = args.narg() >= 1 ? args.optjstring(1, "X").toUpperCase() : "X";
				int minX = args.narg() >= 2 ? args.optint(2, 0) : 0;
				int minY = args.narg() >= 3 ? args.optint(3, 0) : 0;
				int minZ = args.narg() >= 4 ? args.optint(4, 0) : 0;
				int maxX = args.narg() >= 5 ? args.optint(5, dims[0] - 1) : dims[0] - 1;
				int maxY = args.narg() >= 6 ? args.optint(6, dims[1] - 1) : dims[1] - 1;
				int maxZ = args.narg() >= 7 ? args.optint(7, dims[2] - 1) : dims[2] - 1;

				int count = 0;
				for(int x = minX; x <= maxX; x++)
					for(int y = minY; y <= maxY; y++)
						for(int z = minZ; z <= maxZ; z++) {
							if(!inBounds(x, y, z)) continue;
							short type = template.getTypeAt(x, y, z);
							if(type == 0) continue;
							byte orient = template.getOrientationAt(x, y, z);

							int mx = x, my = y, mz = z;
							byte mo = orient;
							switch(axis) {
								case "X":
									mx = (dims[0] - 1) - x;
									if(orient == 4) mo = 5; else if(orient == 5) mo = 4;
									break;
								case "Y":
									my = (dims[1] - 1) - y;
									if(orient == 2) mo = 3; else if(orient == 3) mo = 2;
									break;
								case "Z":
									mz = (dims[2] - 1) - z;
									if(orient == 0) mo = 1; else if(orient == 1) mo = 0;
									break;
							}

							if(inBounds(mx, my, mz) && template.getTypeAt(mx, my, mz) == 0) {
								template.setTypeAt(mx, my, mz, type);
								template.setOrientationAt(mx, my, mz, mo);
								count++;
							}
						}
				totalOps++;
				return LuaValue.valueOf(count);
			}
		});
	}

	/**
	 * Build a Lua table of named orientation constants for all block styles.
	 * These map human-readable names to the integer orientation values that
	 * StarMade uses internally.
	 */
	private static LuaTable buildOrientTable() {
		LuaTable o = new LuaTable();

		// === Basic block orientations (NORMAL style, 6 values) ===
		o.set("FRONT", 0);     // +Z (nose)
		o.set("BACK", 1);      // -Z (tail)
		o.set("TOP", 2);       // +Y
		o.set("BOTTOM", 3);    // -Y
		o.set("RIGHT", 4);     // +X (starboard)
		o.set("LEFT", 5);      // -X (port)

		// === Wedge orientations (12 values) ===
		// A wedge is half a cube cut diagonally. The name describes
		// the flat (full) face and which direction the slope descends toward.
		// "wedge_top_front" = flat top face, slope descends toward front.
		//
		// Top group: slope is on the top surface
		o.set("wedge_top_front", 0);    // slope descends from back toward front on top
		o.set("wedge_top_right", 1);    // slope descends from left toward right on top
		o.set("wedge_top_back", 2);     // slope descends from front toward back on top
		o.set("wedge_top_left", 3);     // slope descends from right toward left on top
		// Bottom group: slope is on the bottom surface
		o.set("wedge_bottom_front", 4); // slope ascends from back toward front on bottom
		o.set("wedge_bottom_right", 5);
		o.set("wedge_bottom_back", 6);
		o.set("wedge_bottom_left", 7);
		// Side group: slope runs vertically along a side
		o.set("wedge_side_front", 8);   // vertical slope facing front
		o.set("wedge_side_right", 9);   // vertical slope facing right
		o.set("wedge_side_back", 10);   // vertical slope facing back
		o.set("wedge_side_left", 11);   // vertical slope facing left

		// === Corner/Spike orientations (24 values) ===
		// A corner is 1/8 of a cube — a triangular spike at one corner.
		// Named by which corner gets the point. Each group of 4 covers
		// the corners visible from that face's perspective.
		//
		// Top-axis group (point sticks up)
		o.set("corner_top_front_right", 0);
		o.set("corner_top_back_right", 1);
		o.set("corner_top_back_left", 2);
		o.set("corner_top_front_left", 3);
		// Bottom-axis group (point sticks down)
		o.set("corner_bottom_front_right", 4);
		o.set("corner_bottom_back_right", 5);
		o.set("corner_bottom_back_left", 6);
		o.set("corner_bottom_front_left", 7);
		// Front-axis group (point sticks toward front)
		o.set("corner_front_top_right", 8);     // SpikeFrontFrontRight
		o.set("corner_front_bottom_right", 9);  // SpikeFrontBackRight
		o.set("corner_front_bottom_left", 10);  // SpikeFrontBackLeft
		o.set("corner_front_top_left", 11);     // SpikeFrontFrontLeft
		// Back-axis group (point sticks toward back)
		o.set("corner_back_top_right", 12);     // SpikeBackFrontRight
		o.set("corner_back_bottom_right", 13);  // SpikeBackBackRight
		o.set("corner_back_bottom_left", 14);   // SpikeBackBackLeft
		o.set("corner_back_top_left", 15);      // SpikeBackFrontLeft
		// Right-axis group (point sticks toward right)
		o.set("corner_right_top_front", 16);    // SpikeRightFrontRight
		o.set("corner_right_bottom_front", 17); // SpikeRightBackRight
		o.set("corner_right_bottom_back", 18);  // SpikeRightBackLeft
		o.set("corner_right_top_back", 19);     // SpikeRightFrontLeft
		// Left-axis group (point sticks toward left)
		o.set("corner_left_top_front", 20);     // SpikeLeftFrontRight
		o.set("corner_left_bottom_front", 21);  // SpikeLeftBackRight
		o.set("corner_left_bottom_back", 22);   // SpikeLeftBackLeft
		o.set("corner_left_top_back", 23);      // SpikeLeftFrontLeft

		// === Tetra orientations (8 values) ===
		// A tetra is 1/4 of a cube — a triangular pyramid at one corner.
		// Named by which corner gets the point.
		o.set("tetra_top_front_right", 0);
		o.set("tetra_top_back_right", 1);
		o.set("tetra_top_back_left", 2);
		o.set("tetra_top_front_left", 3);
		o.set("tetra_bottom_front_right", 4);
		o.set("tetra_bottom_back_right", 5);
		o.set("tetra_bottom_back_left", 6);
		o.set("tetra_bottom_front_left", 7);

		// === Hepta orientations (8 values) ===
		// A hepta is 7/8 of a cube — a cube with one corner cut off.
		// Named by which corner is cut. Same indices as tetra.
		o.set("hepta_top_front_right", 0);
		o.set("hepta_top_back_right", 1);
		o.set("hepta_top_back_left", 2);
		o.set("hepta_top_front_left", 3);
		o.set("hepta_bottom_front_right", 4);
		o.set("hepta_bottom_back_right", 5);
		o.set("hepta_bottom_back_left", 6);
		o.set("hepta_bottom_front_left", 7);

		return o;
	}

	private boolean inBounds(int x, int y, int z) {
		return x >= 0 && x < dims[0] && y >= 0 && y < dims[1] && z >= 0 && z < dims[2];
	}

	private static long packCoord(int x, int y, int z) {
		return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
	}

	/** Find the most common non-air block type along a column. */
	private short findColumnType(int a, int bMin, int bMax, int c, String dir) {
		java.util.Map<Short, Integer> counts = new java.util.HashMap<Short, Integer>();
		short best = 0;
		int bestCount = 0;
		for(int b = bMin; b <= bMax; b++) {
			short t;
			switch(dir) {
				case "X_ROW": t = inBounds(b, a, c) ? template.getTypeAt(b, a, c) : 0; break;
				case "Z_ROW": t = inBounds(a, c, b) ? template.getTypeAt(a, c, b) : 0; break;
				default: t = inBounds(a, b, c) ? template.getTypeAt(a, b, c) : 0; break; // Y
			}
			if(t == 0) continue;
			Integer cnt = counts.get(t);
			int nc = (cnt != null ? cnt : 0) + 1;
			counts.put(t, nc);
			if(nc > bestCount) { bestCount = nc; best = t; }
		}
		return best;
	}

	// Orientation rotation helpers (90 degrees CW when looking down the axis)
	// 0=front(+Z), 1=back(-Z), 2=top(+Y), 3=bottom(-Y), 4=right(+X), 5=left(-X)

	private static byte rotateOrientX(byte o) {
		switch(o) {
			case 2: return 0; // top -> front
			case 0: return 3; // front -> bottom
			case 3: return 1; // bottom -> back
			case 1: return 2; // back -> top
			default: return o; // left/right unchanged
		}
	}

	private static byte rotateOrientY(byte o) {
		switch(o) {
			case 0: return 4; // front -> right
			case 4: return 1; // right -> back
			case 1: return 5; // back -> left
			case 5: return 0; // left -> front
			default: return o; // top/bottom unchanged
		}
	}

	private static byte rotateOrientZ(byte o) {
		switch(o) {
			case 2: return 4; // top -> right
			case 4: return 3; // right -> bottom
			case 3: return 5; // bottom -> left
			case 5: return 2; // left -> top
			default: return o; // front/back unchanged
		}
	}
}
