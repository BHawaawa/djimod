from PIL import Image
im = Image.open(r'preview/v3_ui.png')
im.crop((435, 414, 1170, 805)).resize((1470, 782), Image.LANCZOS).save(r'preview/v3_ui_zoom.png')
