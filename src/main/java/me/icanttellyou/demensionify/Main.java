package me.icanttellyou.demensionify;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Main {
    public static void main(String[] args) {
        String path = String.join(" ", args);

        File file = new File(path);

        boolean is1874;

        short width, length, height;
        byte[] blocks;

        short spawnX, spawnY, spawnZ;
        byte spawnYaw, spawnPitch;

        byte perVisit = 0, perBuild = 0;

        Map<Integer, Integer> physicsEntries = new HashMap<>();
        List<Util.Pair<Integer, Util.Pair<Integer, Byte>>> customBlocks = new ArrayList<>();

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            short firstTwoBytes = Short.reverseBytes(in.readShort());

            is1874 = firstTwoBytes == 1874;

            if (is1874) width = Short.reverseBytes(in.readShort());
            else width = firstTwoBytes;
            length = Short.reverseBytes(in.readShort());
            height = Short.reverseBytes(in.readShort());
            blocks = new byte[width * height * length];
            System.out.println("Read level properties: Width: " + width + ", Height: " + height + ", Length: " + length + ", Uses 1874 header: " + is1874);

            spawnX = Short.reverseBytes(in.readShort());
            spawnZ = Short.reverseBytes(in.readShort());
            spawnY = Short.reverseBytes(in.readShort());

            spawnYaw = in.readByte();
            spawnPitch = in.readByte();

            if (is1874) {
                perVisit = in.readByte();
                perBuild = in.readByte();
            }

            in.readFully(blocks, 0, blocks.length);

            while (in.available() > 0) {
                byte read = in.readByte();

                if (read == 0xFC) {
                    int count = Integer.reverseBytes(in.readInt());

                    for (int i = 0; i < count; i++) {
                        int coord = Integer.reverseBytes(in.readInt());
                        int data = Integer.reverseBytes(in.readInt());

                        physicsEntries.put(coord, data);
                    }
                }

                if (read == 0xBD) {
                    System.out.println("Warning: Level contains custom block information. This tool was not intended for anything other than MCDzienny levels!");

                    for (int y = 0; y < Math.ceil((double) height / 16); y++)
                        for (int z = 0; z < Math.ceil((double) length / 16); z++)
                            for (int x = 0; x < Math.ceil((double) width / 16); x++) {
                                if (in.readByte() != 1) continue;

                                int index = ((y * length) + z) * width + x;

                                for (int i = 0; i < 4096; i++) {
                                    byte val = in.readByte();

                                    if (val > 0)
                                        customBlocks.add(new Util.Pair<>(index, new Util.Pair<>(i, val)));
                                }
                            }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to read level!");
            e.printStackTrace();
            return;
        }

        boolean hasBlockmension = false;

        for (int b = 0; b < blocks.length; b++) {
            byte block = blocks[b];

            if (block >= 66 && block <= 69) {
                hasBlockmension = true;

                switch (block) {
                    case 66 -> blocks[b] = 29;
                    case 67 -> blocks[b] = 21;
                    case 68 -> blocks[b] = 8;
                    case 69 -> blocks[b] = 10;
                }
            }
        }

        if (!hasBlockmension) {
            System.out.println("Level does not contain Blockmension blocks!");
            return;
        } else System.out.println("Level contains Blockmension blocks, blocks have been changed. Saving level.");

        File outFile = new File(file.getParent(), "fixed/" + file.getName());

        try {
            Files.createDirectories(Paths.get(outFile.getParent()));
        } catch (IOException e) {
            System.out.println("Failed to create directory to save level!");
            e.printStackTrace();
            return;
        }

        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(outFile)))) {
            if (is1874) out.writeShort(Short.reverseBytes((short) 1874));

            out.writeShort(Short.reverseBytes(width));
            out.writeShort(Short.reverseBytes(length));
            out.writeShort(Short.reverseBytes(height));

            out.writeShort(Short.reverseBytes(spawnX));
            out.writeShort(Short.reverseBytes(spawnZ));
            out.writeShort(Short.reverseBytes(spawnY));

            out.writeByte(spawnYaw);
            out.writeByte(spawnPitch);

            if (is1874) {
                out.writeByte(perVisit);
                out.writeByte(perBuild);
            }

            out.write(blocks);

            if (physicsEntries.size() > 0) {
                out.writeByte(0xFC);
                out.writeInt(Integer.reverseBytes(physicsEntries.size()));

                for (Map.Entry<Integer, Integer> entry : physicsEntries.entrySet()) {
                    out.writeInt(Integer.reverseBytes(entry.getKey()));
                    out.writeInt(Integer.reverseBytes(entry.getValue()));
                }
            }

            if (customBlocks.size() > 0) {
                out.writeByte(0xBD);

                for (int y = 0; y < Math.ceil((double) height / 16); y++)
                    for (int z = 0; z < Math.ceil((double) length / 16); z++)
                        for (int x = 0; x < Math.ceil((double) width / 16); x++) {
                            int finalX = x;
                            int finalY = y;
                            int finalZ = z;

                            var filtered = customBlocks.stream().filter((var) -> var.one() == ((finalY * length) + finalZ) * width + finalX).toList();

                            if (filtered.size() > 0) {
                                out.writeByte(1);

                                byte[] toWrite = new byte[4096];

                                for (var w : filtered) {
                                    var pair = w.two();

                                    toWrite[pair.one()] = pair.two();
                                }

                                out.write(toWrite);
                            } else out.writeByte(0);
                        }
            }
        } catch (IOException e) {
            System.out.println("Failed to save level!");
            e.printStackTrace();
        }

        System.out.println("Modified level has been saved to fixed/" + file.getName());
    }
}