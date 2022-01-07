/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.browser.customtabs;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.customtabs.ICustomTabsCallback;
import android.support.customtabs.IPostMessageService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A service to receive postMessage related communication from a Custom Tabs provider.
 */
public class PostMessageService extends Service {
    private IPostMessageService.Stub mBinder = new IPostMessageService.Stub() {

        @SuppressWarnings("NullAway")  // onMessageChannelReady accepts null extras.
        @Override
        public void onMessageChannelReady(@NonNull ICustomTabsCallback callback,
                @Nullable Bundle extras) throws RemoteException {
            callback.onMessageChannelReady(extras);
        }

        @SuppressWarnings("NullAway")  // onPostMessage accepts null extras.
        @Override
        public void onPostMessage(@NonNull ICustomTabsCallback callback,
                @NonNull String message, @Nullable Bundle extras) throws RemoteException {
            callback.onPostMessage(message, extras);
        }
    };

    @Override
    @NonNull
    public IBinder onBind(@Nullable Intent intent) {
        return mBinder;
    }
}
