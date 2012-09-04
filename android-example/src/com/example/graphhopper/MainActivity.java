package com.example.graphhopper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import de.jetsli.graph.routing.DijkstraBidirection;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDFullIndex;
import de.jetsli.graph.storage.MemoryGraphSafe;

public class MainActivity extends Activity {
	private Handler handler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Button start = (Button) findViewById(R.id.button1);
		start.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				new Thread() {
					@Override
					public void run() {
						setMyText("start", R.id.editText1);
						try {
							Graph g = createGraph();
							setMyText("created graph " + g.getNodes(), R.id.editText1);
							Location2IDFullIndex locIndex = new Location2IDFullIndex(g);
							int fromId = locIndex.findID(43.727687, 7.418737);
							int toId = locIndex.findID(43.74958, 7.436566);
							setMyText("found ids:" + fromId + "," + toId, R.id.editText1);
							fromId = 1;
							toId = 12;
							Path p = new DijkstraBidirection(g).calcPath(fromId, toId);
							setMyText("found path:" + p.distance() + ", locations:"
									+ p.locations(), R.id.editText1);
						} catch (Exception ex) {
							throw new RuntimeException(ex);
						}
					}
				}.start();
			}
		});
	}

	public void setMyText(final String text, int editTextId) {
		final EditText et = (EditText) findViewById(editTextId);
		handler.post(new Runnable() {

			@Override
			public void run() {
				et.setText(et.getText() + "\n" + text);
			}
		});
	}

	private Graph createGraph() {
		return new MemoryGraphSafe(Environment.getExternalStorageDirectory()
				.getAbsolutePath()
				+ "/andnav/maps/graph-monaco", 10);
	}
}
