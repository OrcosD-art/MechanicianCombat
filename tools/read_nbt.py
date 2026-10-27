import gzip
import struct
import sys


class Reader:
    def __init__(self, data):
        self.data = data
        self.i = 0

    def take(self, n):
        out = self.data[self.i:self.i + n]
        self.i += n
        return out

    def u8(self):
        return self.take(1)[0]

    def i8(self):
        return struct.unpack(">b", self.take(1))[0]

    def i16(self):
        return struct.unpack(">h", self.take(2))[0]

    def u16(self):
        return struct.unpack(">H", self.take(2))[0]

    def i32(self):
        return struct.unpack(">i", self.take(4))[0]

    def i64(self):
        return struct.unpack(">q", self.take(8))[0]

    def f32(self):
        return struct.unpack(">f", self.take(4))[0]

    def f64(self):
        return struct.unpack(">d", self.take(8))[0]

    def string(self):
        return self.take(self.u16()).decode("utf-8", "replace")


def read_payload(r, tag):
    if tag == 1:
        return r.i8()
    if tag == 2:
        return r.i16()
    if tag == 3:
        return r.i32()
    if tag == 4:
        return r.i64()
    if tag == 5:
        return r.f32()
    if tag == 6:
        return r.f64()
    if tag == 7:
        return list(r.take(r.i32()))
    if tag == 8:
        return r.string()
    if tag == 9:
        child = r.u8()
        return [read_payload(r, child) for _ in range(r.i32())]
    if tag == 10:
        out = {}
        while True:
            child = r.u8()
            if child == 0:
                return out
            name = r.string()
            out[name] = read_payload(r, child)
    if tag == 11:
        return [r.i32() for _ in range(r.i32())]
    if tag == 12:
        return [r.i64() for _ in range(r.i32())]
    raise ValueError("unknown tag %s at %s" % (tag, r.i))


def main(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    r = Reader(data)
    tag = r.u8()
    name = r.string()
    root = read_payload(r, tag)
    print("root", name, "tag", tag)
    inv = root.get("Inventory") or root.get("inventory") or []
    for item in inv:
        slot = item.get("Slot", item.get("slot"))
        iid = item.get("id", "?")
        count = item.get("count", item.get("Count", 1))
        components = item.get("components", {})
        print("slot=%s id=%s count=%s components=%s" % (slot, iid, count, components))


if __name__ == "__main__":
    main(sys.argv[1])
