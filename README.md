# v26 asset extraction

This repository contains decoded assets and decompiled Lingo from the v26
Habbo Shockwave client bundle.

## Source

- Input directory: `/home/alex/Downloads/r26_20080915_0408_7984_61ccb5f8b8797a3aba62c1fa2ca80169/`
- Asset extractor: LibreShockwave Java SDK
- Lingo decompiler: ProjectorRays

## Layout

LibreShockwave outputs are grouped by input file basename. Each successfully
parsed file folder contains decoded assets and metadata:

- `bitmaps/` decoded PNG bitmap members
- `text/` STXT and XMED text payloads
- `sounds/` WAV/MP3 sound exports
- `palettes/` palette TSV files
- `raw_chunks/` member-owned raw resource chunks
- `manifest.tsv` asset/member mapping
- `file_info.tsv` parsed file metadata

ProjectorRays Lingo/source dumps are in:

`projectorrays_lingo/<file>/casts/**/*.ls`

ProjectorRays bytecode listings are alongside them:

`projectorrays_lingo/<file>/casts/**/*.lasm`

## Summary

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
- ProjectorRays `.ls` files: 844
- ProjectorRays `.lasm` files: 844
- ProjectorRays decompiled file folders: 280

## Known Failures

These files failed both the LibreShockwave asset parse and the ProjectorRays
decompile pass:

- `hh_furni_armas.cct`
- `hh_furni_armas_50.cct`
- `hh_furni_drken.cct`
- `hh_furni_drken_50.cct`
- `hh_furni_items.cct`
- `hh_furni_items_50.cct`
- `hh_furni_small.cct`
- `hh_furni_special.cct`
- `hh_furni_special_50.cct`
- `hh_people_1.cct`
- `hh_people_2.cct`
- `hh_people_small_1.cct`
- `hh_people_small_2.cct`
- `hh_room_pool_coke2.cct`

## Batch Logs

- `libreshockwave_summary.tsv`
- `projectorrays_lingo/projectorrays_batch.log`
