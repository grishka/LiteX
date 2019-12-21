/*
 * Copyright (C) 2014 The Android Open Source Project
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
package androidx.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Denotes that the annotated element of integer type, represents
 * a logical type and that its value should be one of the explicitly
 * named constants. If the IntDef#flag() attribute is set to true,
 * multiple constants can be combined.
 * <p>
 * Example:
 * <pre><code>
 *  &#64;Retention(SOURCE)
 *  &#64;IntDef({NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
 *  public @interface NavigationMode {}
 *  public static final int NAVIGATION_MODE_STANDARD = 0;
 *  public static final int NAVIGATION_MODE_LIST = 1;
 *  public static final int NAVIGATION_MODE_TABS = 2;
 *  ...
 *  public abstract void setNavigationMode(@NavigationMode int mode);
 *  &#64;NavigationMode
 *  public abstract int getNavigationMode();
 * </code></pre>
 * For a flag, set the flag attribute:
 * <pre><code>
 *  &#64;IntDef(
 *      flag = true,
 *      value = {NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
 * </code></pre>
 *
 * @see LongDef
 */
@Retention(SOURCE)
@Target({ANNOTATION_TYPE})
public @interface IntDef {
    /** Defines the allowed constants for this element */
    int[] value() default {};

    /** Defines whether the constants can be used as a flag, or just as an enum (the default) */
    boolean flag() default false;

    /**
     * Whether any other values are allowed. Normally this is
     * not the case, but this allows you to specify a set of
     * expected constants, which helps code completion in the IDE
     * and documentation generation and so on, but without
     * flagging compilation warnings if other values are specified.
     */
    boolean open() default false;
}
