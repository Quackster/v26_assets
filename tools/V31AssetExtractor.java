import com.libreshockwave.DirectorFile;
import com.libreshockwave.audio.SoundConverter;
import com.libreshockwave.bitmap.Bitmap;
import com.libreshockwave.cast.XmedStyledText;
import com.libreshockwave.chunks.*;
import com.libreshockwave.id.ChunkId;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class V31AssetExtractor {
    private record Counts(int members, int png, int text, int sounds, int palettes, int raw, int scripts, int errors) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: V31AssetExtractor <dcr-dir> <output-dir>");
            System.exit(2);
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        Files.createDirectories(outputDir);

        DirectorFile.setJpegDecoder(V31AssetExtractor::decodeJpeg);

        List<Path> inputs;
        try (var stream = Files.list(inputDir)) {
            inputs = stream
                .filter(Files::isRegularFile)
                .filter(V31AssetExtractor::isDirectorFile)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        }

        int ok = 0;
        int failed = 0;
        List<String> summary = new ArrayList<>();
        summary.add("file\tmembers\tpng\ttext\tsounds\tpalettes\traw\tscripts\terrors");

        for (Path input : inputs) {
            Path fileOut = outputDir.resolve(baseName(input));
            Files.createDirectories(fileOut);
            Counts counts = extractFile(input, fileOut);
            summary.add(input.getFileName() + "\t" + counts.members + "\t" + counts.png + "\t"
                + counts.text + "\t" + counts.sounds + "\t" + counts.palettes + "\t"
                + counts.raw + "\t" + counts.scripts + "\t" + counts.errors);
            if (counts.errors == 0) ok++; else failed++;
            System.out.printf("%s: members=%d png=%d text=%d sounds=%d palettes=%d raw=%d scripts=%d errors=%d%n",
                input.getFileName(), counts.members, counts.png, counts.text, counts.sounds,
                counts.palettes, counts.raw, counts.scripts, counts.errors);
        }

        Files.writeString(outputDir.resolve("libreshockwave_summary.tsv"), String.join("\n", summary) + "\n");
        System.out.printf("Processed %d files (%d clean, %d with extraction errors)%n", inputs.size(), ok, failed);
    }

    private static Counts extractFile(Path input, Path out) {
        int png = 0, text = 0, sounds = 0, palettes = 0, raw = 0, scripts = 0, errors = 0;
        List<String> manifest = new ArrayList<>();
        manifest.add("member_id\tmember_type\tmember_name\tscript_id\tchunk_id\tfourcc\tasset_path");
        List<String> errorLog = new ArrayList<>();

        try {
            DirectorFile file = DirectorFile.load(input);
            file.setBasePath(input.getParent().toString());
            writeFileInfo(file, input, out);

            Path bitmapDir = out.resolve("bitmaps");
            Path textDir = out.resolve("text");
            Path soundDir = out.resolve("sounds");
            Path paletteDir = out.resolve("palettes");
            Path rawDir = out.resolve("raw_chunks");
            Files.createDirectories(bitmapDir);
            Files.createDirectories(textDir);
            Files.createDirectories(soundDir);
            Files.createDirectories(paletteDir);
            Files.createDirectories(rawDir);

            for (CastMemberChunk member : file.getCastMembers()) {
                String stem = memberStem(member);
                if (member.isBitmap()) {
                    try {
                        Optional<Bitmap> bitmap = file.decodeBitmap(member);
                        if (bitmap.isPresent()) {
                            Path asset = bitmapDir.resolve(stem + ".png");
                            ImageIO.write(bitmap.get().toBufferedImage(), "PNG", asset.toFile());
                            manifest.add(row(member, "", "", out.relativize(asset).toString()));
                            png++;
                        }
                    } catch (Exception e) {
                        errors += log(errorLog, input, member, "bitmap", e);
                    }
                }

                try {
                    var textChunks = file.getTextChunksForMember(member);
                    for (int i = 0; i < textChunks.size(); i++) {
                        TextChunk tc = textChunks.get(i);
                        Path asset = textDir.resolve(stem + "_stxt_" + i + ".txt");
                        Files.writeString(asset, tc.text() == null ? "" : tc.text());
                        manifest.add(row(member, tc.id(), "STXT", out.relativize(asset).toString()));
                        text++;
                    }
                    if (member.isTextXtra()) {
                        XmedStyledText xmed = file.getXmedStyledTextForMember(member);
                        if (xmed != null) {
                            Path asset = textDir.resolve(stem + "_xmed.txt");
                            Files.writeString(asset, xmed.text() == null ? "" : xmed.text());
                            manifest.add(row(member, "", "XMED", out.relativize(asset).toString()));
                            text++;
                        }
                    }
                } catch (Exception e) {
                    errors += log(errorLog, input, member, "text", e);
                }

                if (member.isSound()) {
                    try {
                        for (KeyTableChunk.KeyTableEntry entry : entries(file, member)) {
                            Chunk chunk = file.getChunk(entry.sectionId());
                            SoundChunk sound = null;
                            if (chunk instanceof SoundChunk sc) sound = sc;
                            if (chunk instanceof MediaChunk mc) sound = mc.toSoundChunk();
                            if (sound == null) continue;

                            byte[] data = sound.isMp3() ? SoundConverter.extractMp3(sound) : SoundConverter.toWav(sound);
                            if (data == null) continue;
                            String ext = sound.isMp3() ? ".mp3" : ".wav";
                            Path asset = soundDir.resolve(stem + "_" + entry.sectionId().value() + ext);
                            Files.write(asset, data);
                            manifest.add(row(member, entry.sectionId(), entry.fourccString(), out.relativize(asset).toString()));
                            sounds++;
                        }
                    } catch (Exception e) {
                        errors += log(errorLog, input, member, "sound", e);
                    }
                }

                try {
                    ScriptChunk script = member.isScript() ? file.getScriptByContextId(member.scriptId()) : null;
                    if (script != null) scripts++;
                } catch (Exception e) {
                    errors += log(errorLog, input, member, "script-count", e);
                }

                try {
                    for (KeyTableChunk.KeyTableEntry entry : entries(file, member)) {
                        Chunk chunk = file.getChunk(entry.sectionId());
                        byte[] data = rawBytes(chunk);
                        if (data == null) continue;
                        Path asset = rawDir.resolve(stem + "_" + entry.sectionId().value() + "_" + clean(entry.fourccString()) + ".bin");
                        Files.write(asset, data);
                        manifest.add(row(member, entry.sectionId(), entry.fourccString(), out.relativize(asset).toString()));
                        raw++;
                    }
                } catch (Exception e) {
                    errors += log(errorLog, input, member, "raw", e);
                }
            }

            for (PaletteChunk palette : file.getPalettes()) {
                try {
                    Path asset = paletteDir.resolve("palette_" + palette.id().value() + ".tsv");
                    List<String> lines = new ArrayList<>();
                    lines.add("index\tr\tg\tb\thex");
                    for (int i = 0; i < palette.colorCount(); i++) {
                        int rgb = palette.getColor(i);
                        lines.add(i + "\t" + ((rgb >> 16) & 0xff) + "\t" + ((rgb >> 8) & 0xff)
                            + "\t" + (rgb & 0xff) + "\t#" + String.format("%06X", rgb & 0xffffff));
                    }
                    Files.writeString(asset, String.join("\n", lines) + "\n");
                    palettes++;
                } catch (Exception e) {
                    errors += log(errorLog, input, null, "palette", e);
                }
            }

            Files.writeString(out.resolve("manifest.tsv"), String.join("\n", manifest) + "\n");
            if (!errorLog.isEmpty()) Files.writeString(out.resolve("errors.log"), String.join("\n", errorLog) + "\n");
            return new Counts(file.getCastMembers().size(), png, text, sounds, palettes, raw, scripts, errors);
        } catch (Exception e) {
            try {
                Files.writeString(out.resolve("errors.log"), e.toString() + "\n");
            } catch (IOException ignored) {
            }
            return new Counts(0, png, text, sounds, palettes, raw, scripts, errors + 1);
        }
    }

    private static List<KeyTableChunk.KeyTableEntry> entries(DirectorFile file, CastMemberChunk member) {
        KeyTableChunk kt = file.getKeyTable();
        if (kt == null) return List.of();
        return kt.getEntriesForOwner(member.id());
    }

    private static byte[] rawBytes(Chunk chunk) {
        if (chunk instanceof RawChunk rc) return rc.data();
        if (chunk instanceof BitmapChunk bc) return bc.data();
        if (chunk instanceof SoundChunk sc) return sc.audioData();
        if (chunk instanceof MediaChunk mc) return mc.audioData();
        if (chunk instanceof TextChunk tc) return tc.text() == null ? new byte[0] : tc.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return null;
    }

    private static Bitmap decodeJpeg(byte[] data) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return null;
            Bitmap bitmap = new Bitmap(img.getWidth(), img.getHeight(), 32);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    bitmap.setPixel(x, y, img.getRGB(x, y));
                }
            }
            return bitmap;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFileInfo(DirectorFile file, Path input, Path out) throws IOException {
        List<String> lines = List.of(
            "source\t" + input,
            "afterburner\t" + file.isAfterburner(),
            "version\t" + file.getVersion(),
            "movie_type\t" + file.getMovieType(),
            "stage_width\t" + file.getStageWidth(),
            "stage_height\t" + file.getStageHeight(),
            "tempo\t" + file.getTempo(),
            "cast_members\t" + file.getCastMembers().size(),
            "scripts\t" + file.getScripts().size(),
            "palettes\t" + file.getPalettes().size()
        );
        Files.writeString(out.resolve("file_info.tsv"), String.join("\n", lines) + "\n");
    }

    private static int log(List<String> errors, Path input, CastMemberChunk member, String phase, Exception e) {
        errors.add(input.getFileName() + "\t" + (member == null ? "" : member.id().value()) + "\t" + phase + "\t" + e);
        return 1;
    }

    private static String row(CastMemberChunk member, ChunkId chunkId, String fourcc, String assetPath) {
        return row(member, chunkId == null ? "" : Integer.toString(chunkId.value()), fourcc, assetPath);
    }

    private static String row(CastMemberChunk member, String chunkId, String fourcc, String assetPath) {
        return member.id().value() + "\t" + member.memberType() + "\t" + safeCell(member.name())
            + "\t" + member.scriptId() + "\t" + safeCell(chunkId) + "\t" + safeCell(fourcc)
            + "\t" + safeCell(assetPath);
    }

    private static String memberStem(CastMemberChunk member) {
        String name = member.name() == null || member.name().isBlank() ? "member" : member.name();
        return String.format("%05d_%s", member.id().value(), clean(name));
    }

    private static boolean isDirectorFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dcr") || name.endsWith(".cct") || name.endsWith(".cst") || name.endsWith(".dir") || name.endsWith(".dxr");
    }

    private static String baseName(Path p) {
        String s = p.getFileName().toString();
        int dot = s.lastIndexOf('.');
        return clean(dot > 0 ? s.substring(0, dot) : s);
    }

    private static String clean(String s) {
        String cleaned = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (cleaned.isBlank()) return "unnamed";
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String safeCell(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
