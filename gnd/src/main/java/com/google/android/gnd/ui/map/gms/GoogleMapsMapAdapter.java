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

package com.google.android.gnd.ui.map.gms;

import static com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION;
import static java8.util.stream.StreamSupport.stream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gnd.R;
import com.google.android.gnd.model.feature.Feature;
import com.google.android.gnd.model.feature.Point;
import com.google.android.gnd.model.layer.Layer;
import com.google.android.gnd.model.layer.Style;
import com.google.android.gnd.ui.MarkerIconFactory;
import com.google.android.gnd.ui.map.MapAdapter;
import com.google.android.gnd.ui.map.MapPin;
import com.google.common.collect.ImmutableSet;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Wrapper around {@link GoogleMap}, exposing Google Maps SDK functionality to Ground as a {@link
 * MapAdapter}.
 */
class GoogleMapsMapAdapter implements MapAdapter {

  private static final String TAG = GoogleMapsMapAdapter.class.getSimpleName();
  private static final String GEO_JSON_FILE = "gnd-geojson.geojson";
  private final GoogleMap map;
  private final Context context;
  private final MarkerIconFactory markerIconFactory;

  /**
   * References to Google Maps SDK Markers present on the map. Used to sync and update markers with
   * current view and data state.
   */
  private Set<Marker> markers = new HashSet<>();

  private final PublishSubject<MapPin> markerClickSubject = PublishSubject.create();
  private final PublishSubject<Point> dragInteractionSubject = PublishSubject.create();
  private final BehaviorSubject<Point> cameraMoves = BehaviorSubject.create();

  @Nullable private LatLng cameraTargetBeforeDrag;

  public GoogleMapsMapAdapter(GoogleMap map, Context context, MarkerIconFactory markerIconFactory) {
    this.map = map;
    this.context = context;
    this.markerIconFactory = markerIconFactory;
    map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    UiSettings uiSettings = map.getUiSettings();
    uiSettings.setRotateGesturesEnabled(false);
    uiSettings.setTiltGesturesEnabled(false);
    uiSettings.setMyLocationButtonEnabled(false);
    uiSettings.setMapToolbarEnabled(false);
    uiSettings.setCompassEnabled(false);
    uiSettings.setIndoorLevelPickerEnabled(false);
    map.setOnMarkerClickListener(this::onMarkerClick);
    map.setOnCameraIdleListener(this::onCameraIdle);
    map.setOnCameraMoveStartedListener(this::onCameraMoveStarted);
    map.setOnCameraMoveListener(this::onCameraMove);
    onCameraMove();
  }

  public void renderJsonLayer() {
    File file = new File(context.getFilesDir(), GEO_JSON_FILE);

    try {
      InputStream is = new FileInputStream(file);
      BufferedReader buf = new BufferedReader(new InputStreamReader(is));
      String line = buf.readLine();
      StringBuilder sb = new StringBuilder();
      while (line != null) {
        sb.append(line).append('\n');
        line = buf.readLine();
      }

      JSONObject geoJson = new JSONObject(sb.toString());
      GeoJsonLayer layer = new GeoJsonLayer(map, geoJson);
      layer.addLayerToMap();
      Log.d(TAG, "JSON layer successfully loaded");

    } catch (IOException | JSONException e) {
      Log.e(TAG, "Unable to load JSON layer", e);
    }
  }

  private boolean onMarkerClick(Marker marker) {
    if (map.getUiSettings().isZoomGesturesEnabled()) {
      markerClickSubject.onNext((MapPin) marker.getTag());
      // Allow map to pan to marker.
      return false;
    } else {
      // Prevent map from panning to marker.
      return true;
    }
  }

  @Override
  public Observable<MapPin> getMapPinClicks() {
    return markerClickSubject;
  }

  @Override
  public Observable<Point> getDragInteractions() {
    return dragInteractionSubject;
  }

  @Override
  public Observable<Point> getCameraMoves() {
    return cameraMoves;
  }

  @Override
  public void enable() {
    map.getUiSettings().setAllGesturesEnabled(true);
  }

  @Override
  public void disable() {
    map.getUiSettings().setAllGesturesEnabled(false);
  }

  @Override
  public void moveCamera(Point point) {
    map.moveCamera(CameraUpdateFactory.newLatLng(toLatLng(point)));
  }

  @Override
  public void moveCamera(Point point, float zoomLevel) {
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(toLatLng(point), zoomLevel));
  }

  private void addMapPin(MapPin mapPin, boolean hasPendingWrites, boolean isHighlighted) {
    LatLng position = toLatLng(mapPin.getPosition());
    // TODO: Change size and color based on hasPendingWrites and isHighlighted.
    Marker marker =
        map.addMarker(new MarkerOptions().position(position).icon(mapPin.getIcon()).alpha(1.0f));
    marker.setTag(mapPin);
    markers.add(marker);
  }

  private void removeAllMarkers() {
    stream(markers).forEach(Marker::remove);
    markers.clear();
  }

  @Override
  public Point getCameraTarget() {
    return fromLatLng(map.getCameraPosition().target);
  }

  @Override
  public float getCurrentZoomLevel() {
    return map.getCameraPosition().zoom;
  }

  @Override
  @SuppressLint("MissingPermission")
  public void enableCurrentLocationIndicator() {
    if (!map.isMyLocationEnabled()) {
      map.setMyLocationEnabled(true);
    }
  }

  @Override
  public void setFeatures(ImmutableSet<Feature> updatedFeatures) {
    if (updatedFeatures.isEmpty()) {
      removeAllMarkers();
      return;
    }
    Set<Feature> featuresToAdd = new HashSet<>(updatedFeatures);
    Iterator<Marker> it = markers.iterator();
    while (it.hasNext()) {
      Marker marker = it.next();
      MapPin pin = getMapPin(marker);
      Feature feature  = pin.getFeature();
      if (updatedFeatures.contains(feature)) {
        featuresToAdd.remove(feature);
      } else {
        removeMarker(marker);
        it.remove();
      }
    }
    stream(featuresToAdd).forEach(this::addMarker);
  }

  private static Point fromLatLng(LatLng latLng) {
    return Point.newBuilder().setLatitude(latLng.latitude).setLongitude(latLng.longitude).build();
  }

  private static LatLng toLatLng(Point point) {
    return new LatLng(point.getLatitude(), point.getLongitude());
  }

  private MapPin getMapPin(Marker marker) {
    return (MapPin) marker.getTag();
  }

  private void removeMarker(Marker marker) {
    Log.v(TAG, "Removing marker " + marker.getId());
    marker.remove();
  }

  private void addMarker(Feature feature) {
    Log.v(TAG, "Adding marker for " + feature.getId());
    Layer layer = feature.getLayer();
    Style style = layer.getDefaultStyle();
    String color = style == null ? null : style.getColor();
    BitmapDescriptor icon = markerIconFactory.getMarkerIcon(parseColor(color));
    // TODO: Reimplement hasPendingWrites.
    addMapPin(
        MapPin.newBuilder()
            .setId(feature.getId())
            .setPosition(feature.getPoint())
            .setIcon(icon)
            .setFeature(feature)
            .build(),
        false,
        false);
  }

  private int parseColor(@Nullable String colorHexCode) {
    try {
      return Color.parseColor(String.valueOf(colorHexCode));
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Invalid color code in layer style: " + colorHexCode);
      return context.getResources().getColor(R.color.colorMapAccent);
    }
  }

  private void onCameraIdle() {
    cameraTargetBeforeDrag = null;
  }

  private void onCameraMoveStarted(int reason) {
    if (reason == REASON_DEVELOPER_ANIMATION) {
      // MapAdapter was panned by the app, not the user.
      return;
    }
    cameraTargetBeforeDrag = map.getCameraPosition().target;
  }

  private void onCameraMove() {
    LatLng cameraTarget = map.getCameraPosition().target;
    Point target = fromLatLng(cameraTarget);
    cameraMoves.onNext(target);
    if (cameraTargetBeforeDrag != null && !cameraTarget.equals(cameraTargetBeforeDrag)) {
      dragInteractionSubject.onNext(target);
    }
  }
}
