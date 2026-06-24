# v26 asset extraction

Source directory:

`/home/alex/Downloads/r26_20080915_0408_7984_61ccb5f8b8797a3aba62c1fa2ca80169/`

LibreShockwave extraction outputs are grouped by input file name. Each valid
file folder contains decoded assets and metadata:

- `bitmaps/` decoded PNG bitmap members
- `text/` STXT and XMED text payloads
- `sounds/` WAV/MP3 sound exports
- `palettes/` palette TSV files
- `raw_chunks/` member-owned raw resource chunks
- `manifest.tsv` asset/member mapping
- `file_info.tsv` parsed file metadata

Summary:

- Director/Shockwave files scanned: 295
- LibreShockwave clean parses: 281
- Placeholder/parse failures: 14
- Cast members: 23,622
- Decoded PNGs: 19,518
- Text exports: 1,572
- Sound exports: 49
- Palette exports: 1,307
- Raw chunk exports: 21,436
- Script members: 843

Batch logs:

- `libreshockwave_summary.tsv`
