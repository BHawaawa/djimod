import nbtlib
import os
import glob
import struct
import zlib
import io

base = r'fabric_mod/run/saves/RC2 Mouse QA'

def read_region(path):
    with open(path, 'rb') as f:
        header = f.read(8192)
        n = min(1024, len(header) // 4)
        for i in range(n):
            off = struct.unpack('>I', header[i * 4:i * 4 + 4])[0]
            if off == 0:
                continue
            sector = off >> 8
            count = off & 0xFF
            f.seek(sector * 4096)
            raw = f.read(count * 4096)
            length = struct.unpack('>I', raw[:4])[0]
            compression = raw[4]
            data = raw[5:5 + length]
            if compression == 2:
                data = zlib.decompress(data)
            elif compression == 1:
                import gzip
                data = gzip.decompress(data)
            chunk = nbtlib.File.parse(io.BytesIO(data))
            if not isinstance(chunk, nbtlib.Compound):
                continue
            entities = chunk.get('Entities', [])
            for entity in entities:
                eid = str(entity.get('id', ''))
                if 'avata360' in eid or 'mini4pro' in eid:
                    pos = entity.get('Pos')
                    print('drone', eid, 'pos', [round(float(p), 1) for p in pos],
                          'uuid', entity.get('UUID'))

for rf in glob.glob(os.path.join(base, 'entities', 'r.*.*.mca')):
    try:
        read_region(rf)
    except Exception as e:
        print('skip', os.path.basename(rf), e)
