package com.example.graphhopper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.overlay.ListOverlay;
import org.mapsforge.android.maps.overlay.PolygonalChain;
import org.mapsforge.android.maps.overlay.Polyline;
import org.mapsforge.core.model.GeoPoint;
import org.mapsforge.map.reader.header.FileOpenResult;

import android.gesture.GestureOverlayView.OnGestureListener;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.widget.Toast;
import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDIndex;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.storage.MemoryGraphSafe;

public class MainActivity extends MapActivity {
	private static final GeoPoint BRANDENBURG_GATE = new GeoPoint(52.516273, 13.377725);
	private static final GeoPoint VICTORY_COLUMN = new GeoPoint(52.514505, 13.350111);
	private MapView mapView;
	private Graph graph;
	private Location2IDIndex locIndex;
	private GeoPoint start;
	private static final File MAP_FILE = new File(Environment
			.getExternalStorageDirectory().getAbsolutePath()
			+ "/graphhopper/maps/berlin.map");
	ListOverlay pathOverlay = new ListOverlay();
	SimpleOnGestureListener listener = new SimpleOnGestureListener() {

		public boolean onSingleTapConfirmed(MotionEvent event) {
			// TODO use the clicked points and not the center!
			// event.getX(), event.getY()
			GeoPoint tmp = mapView.getMapViewPosition().getMapPosition().geoPoint;
			if (start != null) {
				calcPath(start.latitude, start.longitude, tmp.latitude, tmp.longitude);
				start = null;
			} else {
				log("start routing at " + tmp.latitude + "," + tmp.longitude);
				start = tmp;
			}
			return true;
		}
	};
	GestureDetector gestureDetector = new GestureDetector(listener);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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
			geoPoints.add(new GeoPoint(graph.getLatitude(p.location(i)), graph.getLongitude(p.location(i))));
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

	public void calcPath(double fromLat, double fromLon, double toLat, double toLon) {
		int fromId = locIndex.findID(fromLat, fromLon);
		int toId = locIndex.findID(toLat, toLon);
		log("found ids:" + fromId + "->" + toId + " via coords:" + fromLat + ","
				+ fromLon + "->" + toLat + "," + toLon);
		Path p = new DijkstraBidirection(graph).calcPath(fromId, toId);
		log("found path:" + p.distance() + ", " + p.locations());		

		pathOverlay.getOverlayItems().clear();
		pathOverlay.getOverlayItems().add(createPolyline(p));
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
