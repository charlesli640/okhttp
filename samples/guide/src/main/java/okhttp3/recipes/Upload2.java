/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.recipes;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public final class Upload2 {
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private String filepath = "test.png";

    public static void main(String... args) throws Exception {
        new Upload2().run();
    }

    public void run() throws Exception {
        final ProgressListener progressListener = new ProgressListener() {
            boolean firstUpdate = true;

            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
                if (done) {
                    System.out.println("completed");
                } else {
                    if (firstUpdate) {
                        firstUpdate = false;
                        if (contentLength == -1) {
                            System.out.println("content-length: unknown");
                        } else {
                            System.out.format("content-length: %d\n", contentLength);
                        }
                    }

                    System.out.println(bytesRead);

                    if (contentLength != -1) {
                        System.out.format("Upload2 %d%% done\n", (100 * bytesRead) / contentLength);
                    }
                }
            }
        };

        File sourceFile = new File(filepath);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", sourceFile.getName(),
                        new CountingRequestBody(RequestBody.create(MEDIA_TYPE_PNG, sourceFile),
                                progressListener))
                .build();


        Request request = new Request.Builder()
                .url("http://192.168.1.9:5000/")
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder().build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            System.out.println(response.body().string());
        }
    }

    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

    public class CountingRequestBody extends RequestBody {

        private final ProgressListener listener;
        protected RequestBody delegate;
        protected CountingSink countingSink;

        public CountingRequestBody(RequestBody delegate, ProgressListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            try {
                return delegate.contentLength();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {

            countingSink = new CountingSink(sink);
            BufferedSink bufferedSink = Okio.buffer(countingSink);

            delegate.writeTo(bufferedSink);

            bufferedSink.flush();
        }

        protected final class CountingSink extends ForwardingSink {

            private long bytesWritten = 0;

            public CountingSink(Sink delegate) {
                super(delegate);
            }

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);

                bytesWritten += byteCount;
                listener.update(bytesWritten, contentLength(), bytesWritten == contentLength());
            }

        }
    }
}
