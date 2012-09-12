package com.example.graphhopper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.Projection;
import org.mapsforge.android.maps.overlay.ListOverlay;
import org.mapsforge.android.maps.overlay.Marker;
import org.mapsforge.android.maps.overlay.Overlay;
import org.mapsforge.android.maps.overlay.OverlayItem;
import org.mapsforge.android.maps.overlay.PolygonalChain;
import org.mapsforge.android.maps.overlay.Polyline;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.GeoPoint;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.reader.header.FileOpenResult;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.Toast;
import de.jetsli.graph.routing.AStar;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.util.FastestCalc;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.storage.MemoryGraphSafe;
import de.jetsli.graph.util.StopWatch;

public class MainActivity extends MapActivity {

	private MapView mapView;
	private Graph graph;
	private Location2IDIndex locIndex;
	private GeoPoint start;
	private static final File MAP_FILE = new File(Environment
			.getExternalStorageDirectory().getAbsolutePath()
			+ "/graphhopper/maps/berlin.map");
	ListOverlay pathOverlay = new ListOverlay();
	SimpleOnGestureListener listener = new SimpleOnGestureListener() {

		public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
			float x = motionEvent.getX();
			float y = motionEvent.getY();
			// byte zoomLevel = mapView.getMapViewPosition().getZoomLevel();

			Projection p = mapView.getProjection();
			GeoPoint tmp = p.fromPixels((int) x, (int) y);

			if (start != null) {
				calcPath(start.latitude, start.longitude, tmp.latitude, tmp.longitude);
				start = null;
			} else {
				start = tmp;
			}
			return true;
		}
	};
	GestureDetector gestureDetector = new GestureDetector(listener);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);

		graph = createGraph();
		locIndex = new Location2IDQuadtree(graph).prepareIndex(2000);
		mapView = new MapView(this) {

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (gestureDetector.onTouchEvent(event))
					return true;
				return super.onTouchEvent(event);
			}
		};
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		FileOpenResult fileOpenResult = mapView.setMapFile(MAP_FILE);
		if (!fileOpenResult.isSuccess()) {
			Toast.makeText(this, fileOpenResult.getErrorMessage(), Toast.LENGTH_LONG)
					.show();
			finish();
		}
		setContentView(mapView);

		mapView.getOverlays().add(pathOverlay);
	}

	private Polyline createPolyline(Path p) {
		int locs = p.locations();
		List<GeoPoint> geoPoints = new ArrayList<GeoPoint>(locs);
		for (int i = 0; i < locs; i++) {
			geoPoints.add(toGeoPoint(p, i));
		}
		PolygonalChain polygonalChain = new PolygonalChain(geoPoints);
		Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setColor(Color.BLUE);
		paintStroke.setAlpha(128);
		paintStroke.setStrokeWidth(8);
		paintStroke.setPathEffect(new DashPathEffect(new float[] { 25, 15 }, 0));

		return new Polyline(polygonalChain, paintStroke);
	}

	private GeoPoint toGeoPoint(Path p, int i) {
		int index = p.location(i);
		return new GeoPoint(graph.getLatitude(index), graph.getLongitude(index));
	}

	private Marker createStartMarker(Path p) {
		if (p.locations() == 0)
			return null;

		Drawable drawable = getResources().getDrawable(R.drawable.flag_red);
		return new Marker(toGeoPoint(p, p.locations() - 1), Marker
				.boundCenterBottom(drawable));
	}

	private Marker createEndMarker(Path p) {
		if (p.locations() == 0)
			return null;

		Drawable drawable = getResources().getDrawable(R.drawable.flag_green);
		return new Marker(toGeoPoint(p, 0), Marker.boundCenterBottom(drawable));
	}

	public void calcPath(double fromLat, double fromLon, double toLat, double toLon) {
		StopWatch sw = new StopWatch().start();
		int fromId = locIndex.findID(fromLat, fromLon);
		int toId = locIndex.findID(toLat, toLon);
		float locFind = sw.stop().getSeconds();
		sw = new StopWatch().start();
		Path p = new AStar(graph).setType(FastestCalc.DEFAULT).calcPath(fromId, toId);

		log("found path! coords:" + fromLat + "," + fromLon + "->" + toLat + "," + toLon
				+ " distance:" + p.distance() + ", " + p.locations() + " time:"
				+ sw.stop().getSeconds() + " locFindTime:" + locFind);

		pathOverlay.getOverlayItems().clear();
		pathOverlay.getOverlayItems().add(createPolyline(p));
		Marker start = createStartMarker(p);
		if (start != null)
			pathOverlay.getOverlayItems().add(start);
		Marker m = createEndMarker(p);
		if (m != null)
			pathOverlay.getOverlayItems().add(m);
		mapView.redraw();
	}

	private void log(String str) {
		Log.i("GH", str);
	}

	private Graph createGraph() {
		return new MemoryGraphSafe(Environment.getExternalStorageDirectory()
				.getAbsolutePath()
				+ "/graphhopper/maps/graph-berlin.osm", 10);
	}
}
