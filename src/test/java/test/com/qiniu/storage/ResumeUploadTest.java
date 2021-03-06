package test.com.qiniu.storage;

import com.qiniu.common.QiniuException;
import com.qiniu.common.Region;
import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.ResumeUploader;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Etag;
import com.qiniu.util.StringMap;
import org.junit.Test;
import test.com.qiniu.TempFile;
import test.com.qiniu.TestConfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ResumeUploadTest {

    /**
     * 检测自定义变量foo是否生效
     *
     * @throws IOException
     */
    @Test
    public void testXVar() throws IOException {

        Map<String, Region> bucketKeyMap = new HashMap<String, Region>();
        bucketKeyMap.put(TestConfig.testBucket_z0, Region.region0());
        bucketKeyMap.put(TestConfig.testBucket_na0, Region.regionNa0());
        for (Map.Entry<String, Region> entry : bucketKeyMap.entrySet()) {
            String bucket = entry.getKey();
            Region region = entry.getValue();
            final String expectKey = "世/界";
            File f = null;
            try {
                f = TempFile.createFile(1024 * 4 + 2341);
            } catch (IOException e) {
                e.printStackTrace();
            }
            assert f != null;
            StringMap params = new StringMap().put("x:foo", "foo_val");
            final String returnBody = "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"fsize\":\"$(fsize)\""
                    + ",\"fname\":\"$(fname)\",\"mimeType\":\"$(mimeType)\",\"foo\":\"$(x:foo)\"}";
            String token = TestConfig.testAuth.uploadToken(bucket, expectKey, 3600,
                    new StringMap().put("returnBody", returnBody));

            try {
                UploadManager uploadManager = new UploadManager(new Configuration(region));
                Response res = uploadManager.put(f, expectKey, token, params, null, true);
                StringMap m = res.jsonToMap();
                assertEquals("foo_val", m.get("foo"));
            } catch (QiniuException e) {
                assertEquals("", e.response == null ? "e.response is null" : e.response.bodyString());
                fail();
            } finally {
                TempFile.remove(f);
            }
        }
    }

    /**
     * 分片上传
     * 检测key、hash、fszie、fname是否符合预期
     *
     * @param size
     * @param https
     * @throws IOException
     */
    private void template(int size, boolean https) throws IOException {
        Map<String, Region> bucketKeyMap = new HashMap<String, Region>();
        bucketKeyMap.put(TestConfig.testBucket_z0, Region.region0());
        bucketKeyMap.put(TestConfig.testBucket_na0, Region.regionNa0());
        for (Map.Entry<String, Region> entry : bucketKeyMap.entrySet()) {
            String bucket = entry.getKey();
            Region region = entry.getValue();
            Configuration c = new Configuration(region);
            c.useHttpsDomains = https;
            final String expectKey = "\r\n?&r=" + size + "k";
            final File f = TempFile.createFile(size);
            final String etag = Etag.file(f);
            final String returnBody = "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"fsize\":\"$(fsize)\""
                    + ",\"fname\":\"$(fname)\",\"mimeType\":\"$(mimeType)\"}";
            String token = TestConfig.testAuth.uploadToken(bucket, expectKey, 3600,
                    new StringMap().put("returnBody", returnBody));

            try {
                ResumeUploader up = new ResumeUploader(new Client(), token, expectKey, f, null, null, null,
                        new Configuration(region));
                Response r = up.upload();
                MyRet ret = r.jsonToObject(MyRet.class);
                assertEquals(expectKey, ret.key);
                assertEquals(f.getName(), ret.fname);
                assertEquals(String.valueOf(f.length()), ret.fsize);
                assertEquals(etag, ret.hash);
            } catch (QiniuException e) {
                assertEquals("", e.response == null ? "e.response is null" : e.response.bodyString());
                fail();
            }
            TempFile.remove(f);
        }
    }

    @Test
    public void test1K() throws Throwable {
        template(1, false);
    }

    @Test
    public void test600k() throws Throwable {
        template(600, true);
    }

    @Test
    public void test600k2() throws IOException {
        template(600, false);
    }

    @Test
    public void test4M() throws Throwable {
        if (TestConfig.isTravis()) {
            return;
        }
        template(1024 * 4, false);
    }

    @Test
    public void test8M1k() throws Throwable {
        if (TestConfig.isTravis()) {
            return;
        }
        template(1024 * 8 + 1, false);
    }

    @Test
    public void test8M1k2() throws Throwable {
        if (TestConfig.isTravis()) {
            return;
        }
        template(1024 * 8 + 1, true);
    }

    class MyRet {
        public String hash;
        public String key;
        public String fsize;
        public String fname;
        public String mimeType;
    }
}
