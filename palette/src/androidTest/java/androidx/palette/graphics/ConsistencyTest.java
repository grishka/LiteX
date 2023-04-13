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

package androidx.palette.graphics;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsistencyTest {

    private static final int NUMBER_TRIALS = 10;

    @Test
    @SmallTest
    public void testConsistency() {
        Palette lastPalette = null;
        final Bitmap bitmap = TestUtils.loadSampleBitmap();

        for (int i = 0; i < NUMBER_TRIALS; i++) {
            Palette newPalette = Palette.from(bitmap).generate();
            if (lastPalette != null) {
                assetPalettesEqual(lastPalette, newPalette);
            }
            lastPalette = newPalette;
        }
    }

    private static void assetPalettesEqual(Palette p1, Palette p2) {
        assertEquals(p1.getVibrantSwatch(), p2.getVibrantSwatch());
        assertEquals(p1.getLightVibrantSwatch(), p2.getLightVibrantSwatch());
        assertEquals(p1.getDarkVibrantSwatch(), p2.getDarkVibrantSwatch());
        assertEquals(p1.getMutedSwatch(), p2.getMutedSwatch());
        assertEquals(p1.getLightMutedSwatch(), p2.getLightMutedSwatch());
        assertEquals(p1.getDarkMutedSwatch(), p2.getDarkMutedSwatch());
    }
}
