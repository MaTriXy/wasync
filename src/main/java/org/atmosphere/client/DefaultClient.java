/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;

public class DefaultClient implements Client {

    private AsyncHttpClient asyncHttpClient;

    protected DefaultClient() {
    }

    public Socket create() {
        AsyncHttpClientConfig.Builder config = new AsyncHttpClientConfig.Builder();
        config.setFollowRedirects(true).setRequestTimeoutInMs(-1);
        asyncHttpClient = new AsyncHttpClient();
        return new DefaultSocket(asyncHttpClient);
    }

    public Socket create(Options options) {
        // TODO
        AsyncHttpClientConfig.Builder config = new AsyncHttpClientConfig.Builder();
        asyncHttpClient = new AsyncHttpClient(config.build());
        return new DefaultSocket(asyncHttpClient);
    }

}
