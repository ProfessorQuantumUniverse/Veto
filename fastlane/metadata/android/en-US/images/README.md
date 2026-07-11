# Store images

F-Droid and Google Play need raster images (PNG or JPG) here. The editable sources
are vector SVGs in `docs/store/svg/`. Export them to PNG into this folder:

Expected files:

* `featureGraphic.png` (1024 x 500)
* `icon.png` (512 x 512, optional; F-Droid can also take the launcher icon)
* `phoneScreenshots/1.png` ... `phoneScreenshots/5.png` (1080 x 2160)

How to export without extra tools:

1. `cd docs/store`
2. `python -m http.server 8000`
3. Open `http://localhost:8000/export.html` in a browser
4. Click "Download all PNG", then move the files into this folder

The screenshot order in `phoneScreenshots` controls the order shown in the store.
