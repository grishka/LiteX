/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.customtabs;

import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.ICustomTabsCallback;

/**
 * Interface to a PostMessageService.
 * @hide
 */
interface IPostMessageService {
    void onMessageChannelReady(in ICustomTabsCallback callback, in Bundle extras) = 1;
    void onPostMessage(in ICustomTabsCallback callback, String message, in Bundle extras) = 2;
}
