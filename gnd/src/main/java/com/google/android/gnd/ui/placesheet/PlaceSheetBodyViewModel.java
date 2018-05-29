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

package com.google.android.gnd.ui.placesheet;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import com.google.android.gnd.model.Form;
import com.google.android.gnd.ui.BottomSheetEvent;
import java.util.List;
import javax.inject.Inject;

public class PlaceSheetBodyViewModel extends ViewModel {
  private MutableLiveData<PlaceSheetBodyUpdate> placeSheetBodyUpdates;

  @Inject
  public PlaceSheetBodyViewModel() {
    placeSheetBodyUpdates = new MutableLiveData<PlaceSheetBodyUpdate>();
  }

  void onBottomSheetEvent(BottomSheetEvent bottomSheetEvent) {
    if (bottomSheetEvent.getType().equals(BottomSheetEvent.Type.SHOW)) {
      placeSheetBodyUpdates.setValue(
        PlaceSheetBodyUpdate.reset(bottomSheetEvent.getPlaceType().getFormsList()));
    }
  }

  LiveData<PlaceSheetBodyUpdate> getPlaceSheetBodyUpdates() {
    return placeSheetBodyUpdates;
  }

  static class PlaceSheetBodyUpdate {
    private List<Form> forms;

    public PlaceSheetBodyUpdate(List<Form> forms) {
      this.forms = forms;
    }

    static PlaceSheetBodyUpdate reset(List<Form> forms) {
      return new PlaceSheetBodyUpdate(forms);
    }

    public List<Form> getForms() {
      return forms;
    }
  }
}
