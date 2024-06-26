package dev.zarr.zarrjava.store;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import dev.zarr.zarrjava.utils.Utils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class S3Store implements Store, Store.ListableStore {

  @Nonnull
  private final AmazonS3 s3client;
  @Nonnull
  private final String bucketName;
  @Nullable
  private final String prefix;

  public S3Store(@Nonnull AmazonS3 s3client, @Nonnull String bucketName, @Nullable String prefix) {
    this.s3client = s3client;
    this.bucketName = bucketName;
    this.prefix = prefix;
  }

  String resolveKeys(String[] keys) {
    if (prefix == null) {
      return String.join("/", keys);
    }
    if (keys == null || keys.length == 0) {
      return prefix;
    }
    return prefix + "/" + String.join("/", keys);
  }

  @Nullable
  ByteBuffer get(GetObjectRequest getObjectRequest) {
    try (S3ObjectInputStream inputStream = s3client.getObject(getObjectRequest)
        .getObjectContent()) {
      return Utils.asByteBuffer(inputStream);
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public boolean exists(String[] keys) {
    return s3client.doesObjectExist(bucketName, resolveKeys(keys));
  }

  @Nullable
  @Override
  public ByteBuffer get(String[] keys) {
    return get(new GetObjectRequest(bucketName, resolveKeys(keys)));
  }

  @Nullable
  @Override
  public ByteBuffer get(String[] keys, long start) {
    return get(new GetObjectRequest(bucketName, resolveKeys(keys)).withRange(start));
  }

  @Nullable
  @Override
  public ByteBuffer get(String[] keys, long start, long end) {
    return get(new GetObjectRequest(bucketName, resolveKeys(keys)).withRange(start, end));
  }

  @Override
  public void set(String[] keys, ByteBuffer bytes) {
    try (InputStream byteStream = new ByteArrayInputStream(Utils.toArray(bytes))) {
      s3client.putObject(bucketName, resolveKeys(keys), byteStream, new ObjectMetadata());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void delete(String[] keys) {
    s3client.deleteObject(bucketName, resolveKeys(keys));
  }

  @Override
  public Stream<String> list(String[] keys) {
    final String fullKey = resolveKeys(keys);
    return s3client.listObjects(bucketName, fullKey)
        .getObjectSummaries()
        .stream()
        .map(p -> p.getKey().substring(fullKey.length() + 1));
  }

  @Nonnull
  @Override
  public StoreHandle resolve(String... keys) {
    return new StoreHandle(this, keys);
  }

  @Override
  public String toString() {
    return "s3://" + bucketName + "/" + prefix;
  }
}
