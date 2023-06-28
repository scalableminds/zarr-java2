package com.scalableminds.zarrjava;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalableminds.zarrjava.store.FilesystemStore;
import com.scalableminds.zarrjava.store.HttpStore;
import com.scalableminds.zarrjava.store.S3Store;
import com.scalableminds.zarrjava.utils.MultiArrayUtils;
import com.scalableminds.zarrjava.v3.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ZarrTest {

    final Path TESTDATA = Paths.get("testdata");
    final Path TESTOUTPUT = Paths.get("testoutput");

    @Before
    public void clearTestoutputFolder() throws IOException {
        if (Files.exists(TESTOUTPUT)) {
            try (Stream<Path> walk = Files.walk(TESTOUTPUT)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectory(TESTOUTPUT);
    }

    @Test
    public void testStores() throws IOException, ZarrException {
        FilesystemStore fsStore = new FilesystemStore(TESTOUTPUT);
        HttpStore httpStore = new HttpStore("https://static.webknossos.org/data/");
        S3Store s3Store = new S3Store(AmazonS3ClientBuilder.standard().withRegion("eu-west-1").withCredentials(
                new ProfileCredentialsProvider()).build(), "static.webknossos.org", "data");

        ObjectMapper objectMapper = Node.makeObjectMapper();

        GroupMetadata group = objectMapper.readValue(
                Files.readAllBytes(TESTDATA.resolve("l4_sample_no_sharding").resolve("zarr.json")),
                GroupMetadata.class);

        System.out.println(group);
        System.out.println(objectMapper.writeValueAsString(group));

        ArrayMetadata arrayMetadata = objectMapper.readValue(Files.readAllBytes(
                        TESTDATA.resolve("l4_sample_no_sharding").resolve("color").resolve("1").resolve("zarr.json")),
                ArrayMetadata.class);

        System.out.println(arrayMetadata);
        System.out.println(objectMapper.writeValueAsString(arrayMetadata));


        System.out.println(Array.open(new FilesystemStore(TESTDATA).resolve("l4_sample_no_sharding", "color", "1")));
        // System.out.println(new Group(fsStore, "l4_sample_no_sharding").list());
        // System.out.println(((Group) new Group(fsStore, "l4_sample_no_sharding").get("color")).list());

        System.out.println(com.scalableminds.zarrjava.v2.Array.open(httpStore.resolve("l4_sample", "color", "1")));

        System.out.println(Array.open(httpStore.resolve("zarr_v3", "l4_sample", "color", "1")));
        System.out.println(Array.open(s3Store.resolve("zarr_v3", "l4_sample", "color", "1")));

    }

    @Test
    public void testV3ShardingReadCutout() throws IOException, ZarrException {
        Array array = Array.open(new FilesystemStore(TESTDATA).resolve("l4_sample", "color", "1"));

        ucar.ma2.Array outArray = array.read(new long[]{0, 3073, 3073, 513}, new int[]{1, 64, 64, 64});
        assertEquals(outArray.getSize(), 64 * 64 * 64);
        assertEquals(outArray.getByte(0), -98);
    }

    @Test
    public void testV3ShardingReadWrite() throws IOException, ZarrException {
        Array readArray = Array.open(new FilesystemStore(TESTDATA).resolve("l4_sample", "color", "4-4-1"));
        ucar.ma2.Array readArrayContent = readArray.read();
        Array writeArray = Array.create(new FilesystemStore(TESTOUTPUT).resolve("l4_sample_2", "color", "4-4-1"),
                ArrayMetadata.builder().withShape(readArray.metadata.shape).withDataType(
                        readArray.metadata.dataType).withChunkShape(readArray.metadata.chunkShape()).withFillValue(
                        readArray.metadata.fillValue).withCodecs(readArray.metadata.codecs).build());
        writeArray.write(readArrayContent);
        ucar.ma2.Array outArray = writeArray.read();

        assert MultiArrayUtils.allValuesEqual(outArray, readArrayContent);
    }

    @Test
    public void testV3FillValue() throws ZarrException {
        assertEquals((int) ArrayMetadata.parseFillValue(0, DataType.UINT32), 0);
        assertEquals((int) ArrayMetadata.parseFillValue("0x00010203", DataType.UINT32), 50462976);
        assertEquals((byte) ArrayMetadata.parseFillValue("0b00000010", DataType.UINT8), 2);
        assert Double.isNaN((double) ArrayMetadata.parseFillValue("NaN", DataType.FLOAT64));
        assert Double.isInfinite((double) ArrayMetadata.parseFillValue("-Infinity", DataType.FLOAT64));
    }

    @Test
    public void testV3Group() throws IOException, ZarrException {
        FilesystemStore fsStore = new FilesystemStore(TESTOUTPUT);

        Group group = Group.create(fsStore.resolve("testgroup"));
        Group group2 = group.createGroup("test2", GroupMetadata.builder().withAttribute("hello", "world").build());
        Array array = group2.createArray("array",
                ArrayMetadata.builder().withShape(10, 10).withDataType(DataType.UINT8).withChunkShape(5,
                        5).withFillValue(0).build());
        array.write(new long[]{2, 2}, ucar.ma2.Array.factory(ucar.ma2.DataType.UBYTE, new int[]{8, 8}));

        assertArrayEquals(((Array) ((Group) group.list()[0]).list()[0]).metadata.chunkShape(), new int[]{5, 5});
    }

    @Test
    public void testV2() throws IOException, ZarrException {
        FilesystemStore fsStore = new FilesystemStore("");
        HttpStore httpStore = new HttpStore("https://static.webknossos.org/data");

        System.out.println(com.scalableminds.zarrjava.v2.Array.open(httpStore.resolve("l4_sample", "color", "1")));
    }
}