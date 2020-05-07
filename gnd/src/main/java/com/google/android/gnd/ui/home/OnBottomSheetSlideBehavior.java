/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.ui.home;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.gnd.R;

public abstract class OnBottomSheetSlideBehavior<V extends View>
    extends CoordinatorLayout.Behavior<V> {
  public OnBottomSheetSlideBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  protected abstract void onSheetScrolled(
      CoordinatorLayout parent, V child, HomeScreenMetrics metrics);

  @Override
  public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
    return dependency.getId() == R.id.bottom_sheet_scroll_view;
  }

  @Override
  public boolean onDependentViewChanged(CoordinatorLayout parent, V child, View dependency) {
    onSheetScrolled(parent, child, new HomeScreenMetrics(parent, dependency));
    return false;
  }
}
