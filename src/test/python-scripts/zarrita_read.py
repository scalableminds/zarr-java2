import sys

import numpy as np
import zarrita
from zarrita.metadata import ShardingCodecIndexLocation

codec_string = sys.argv[1]
param_string = sys.argv[2]
codec = []
if codec_string == "blosc":
    cname, shuffle, clevel = param_string.split("_")
    codec = [zarrita.codecs.bytes_codec(),
             zarrita.codecs.blosc_codec(typesize=4, cname=cname, shuffle=shuffle, clevel=int(clevel))]
elif codec_string == "gzip":
    codec = [zarrita.codecs.bytes_codec(), zarrita.codecs.gzip_codec(level=int(param_string))]
elif codec_string == "zstd":
    level, checksum = param_string.split("_")
    codec = [zarrita.codecs.bytes_codec(), zarrita.codecs.zstd_codec(checksum=checksum == 'true', level=int(level))]
elif codec_string == "bytes":
    codec = [zarrita.codecs.bytes_codec(endian=param_string.lower())]
elif codec_string == "transpose":
    codec = [zarrita.codecs.transpose_codec((1, 0, 2)), zarrita.codecs.bytes_codec()]
elif codec_string == "sharding":
    codec = zarrita.codecs.sharding_codec(chunk_shape=(2, 2, 4), codecs=[zarrita.codecs.bytes_codec("little")],
                                          index_location=ShardingCodecIndexLocation.start if param_string == "start"
                                          else ShardingCodecIndexLocation.end),
elif codec_string == "sharding_nested":
    codec = zarrita.codecs.sharding_codec(chunk_shape=(2, 2, 4),
                                          codecs=[zarrita.codecs.sharding_codec(chunk_shape=(2, 1, 2), codecs=[
                                              zarrita.codecs.bytes_codec("little")])]),
elif codec_string == "crc32c":
    codec = [zarrita.codecs.bytes_codec(), zarrita.codecs.crc32c_codec()]
else:
    raise ValueError(f"Invalid {codec=}")

store = zarrita.LocalStore(sys.argv[3])
expected_data = np.arange(16 * 16 * 16, dtype='int32').reshape(16, 16, 16)

a = zarrita.Array.open(store / 'write_to_zarrita' / codec_string / param_string)
read_data = a[:, :]
assert np.array_equal(read_data, expected_data), f"got:\n {read_data} \nbut expected:\n {expected_data}"

b = zarrita.Array.create(
    store / 'read_from_zarrita_expected' / codec_string / param_string,
    shape=(16, 16, 16),
    chunk_shape=(2, 4, 8),
    dtype="uint32",
    fill_value=0,
    attributes={'test_key': 'test_value'},
    codecs=codec
)

assert a.metadata == b.metadata, f"not equal: \n{a.metadata=}\n{b.metadata=}"
