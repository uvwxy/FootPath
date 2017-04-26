package de.uvwxy.footpathtester;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import de.uvwxy.footpath2.FootPath;
import de.uvwxy.footpath2.drawing.DrawToCanvasWrapper;
import de.uvwxy.footpath2.drawing.OSM2DBuilding;
import de.uvwxy.footpath2.drawing.PaintBoxDrawToCanvasWrapper;
import de.uvwxy.footpath2.drawing.TileLoaderAndPainter;
import de.uvwxy.footpath2.gui.FlowPathTestGUI;
import de.uvwxy.footpath2.gui.MapFileSelector;
import de.uvwxy.footpath2.gui.StepDetectionConfig;
import de.uvwxy.footpath2.map.IndoorLocation;
import de.uvwxy.footpath2.map.IndoorLocationList;
import de.uvwxy.footpath2.matching.MatchingAlgorithm;
import de.uvwxy.footpath2.matching.multifit.MultiFit;
import de.uvwxy.footpath2.tools.FootPathException;
import de.uvwxy.footpath2.tools.GeoUtils;
import de.uvwxy.footpath2.tools.PanZoomListener;
import de.uvwxy.footpath2.tools.PanZoomResult;
import de.uvwxy.footpath2.types.FP_LocationProvider;
import de.uvwxy.footpath2.types.FP_MatchingAlgorithm;
import de.uvwxy.footpath2.types.FP_MovementDetection;

public class FootPathTestGuiActivity extends Activity implements OnTouchListener {
	private final int CALLED_FOR_FILE_PATH = 7;
	private FootPath fp;
	String selectedFilePath;
	String[] loadedRooms;
	String roomLocation;
	String roomDestination;
	IndoorLocationList path;
	PaintBoxDrawToCanvasWrapper paintBoxPath;
	TileLoaderAndPainter tlap = new TileLoaderAndPainter();
	// BuildingLoaderAndPainter blap = new BuildingLoaderAndPainter();
	OSM2DBuilding o2db;

	private TextView tvInfo = null;
	PanZoomListener p = new PanZoomListener();

	private boolean tabletMode = false;

	private Button button1 = null;
	private Button button2 = null;
	private Button button3 = null;
	private Button button4 = null;
	private Button button5 = null;

	private static final int MENU_GRP_ID = 1337;
	private static final int MENU_ITEM_0 = 10;
	private static final int MENU_ITEM_1 = 20;
	private static final int MENU_ITEM_2 = 30;
	// add more here...

	private static final String menu_str_0 = "FlowPath Test UI";
	private static final String menu_str_1 = "Toggle Tablet/Phone Mode";
	private static final String menu_str_2 = "Item 2";

	// add more here...

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(MENU_GRP_ID, MENU_ITEM_0, Menu.NONE, menu_str_0);
		menu.add(MENU_GRP_ID, MENU_ITEM_1, Menu.NONE, menu_str_1);
		// menu.add(MENU_GRP_ID, MENU_ITEM_2, Menu.NONE, menu_str_2);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ITEM_0:
			// Menu Item 0 Clicked
			break;
		case MENU_ITEM_1:
			// Menu Item 1 Clicked
			tabletMode = !tabletMode;
			if (tabletMode)
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else
				this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		case MENU_ITEM_2:
			// Menu Item 2 Clicked
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		tvInfo = (TextView) findViewById(R.id.tvInfo);
		Log.i("FOOTPATH", "Creating footpath object");

		paintBoxPath = (PaintBoxDrawToCanvasWrapper) findViewById(R.id.paintBoxDrawToCanvasWrapper1);

		fp = FootPath.getInstance(this);

		button1 = (Button) findViewById(R.id.button1);
		button2 = (Button) findViewById(R.id.button2);
		button3 = (Button) findViewById(R.id.button3);
		button4 = (Button) findViewById(R.id.button4);
		button5 = (Button) findViewById(R.id.button5);

		button3.setEnabled(false);
		button4.setEnabled(false);
		button5.setEnabled(false);

		p.setOnTouchReturn(false);
		paintBoxPath.setOnTouchListener(this);

		// I am currently developing on my tablet, change below to portrait if needed
		if (tabletMode)
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		else
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

	}

	@Override
	protected void onPause() {
		super.onPause();
		fp._j_stop();
	};

	public void launchStepDetectionConfig(View v) {
		Intent my_intent = new Intent(FootPathTestGuiActivity.this, StepDetectionConfig.class);
		// TODO: below is not read from the Activity
		my_intent.putExtra("username", "testuser");
		startActivityForResult(my_intent, 1);
	}

	public void launchMapFileSelector(View v) {
		// selectedFilePath = "/mnt/sdcard/footpath/multifit_test_parkplatz_small.osm";
		// loadSelectedMapData(null);

		// return;
		Intent my_intent = new Intent(FootPathTestGuiActivity.this, MapFileSelector.class);
		// Below is read from the Activity
		my_intent.putExtra("INIT_PATH", "/mnt/sdcard/footpath/");
		my_intent.putExtra("FILTER", "osm,xml");
		startActivityForResult(my_intent, CALLED_FOR_FILE_PATH);
	}

	public void loadSelectedMapData(View v) {

		if (selectedFilePath == null) {
			Toast.makeText(this, "no file selected", Toast.LENGTH_LONG).show();
			return;
		}

		ProgressDialog dialog = ProgressDialog.show(this, "", "Loading. Please wait...", true);
		fp._a4_loadMapDataFromXMLFile(selectedFilePath);
		o2db = fp._debug_getOSM2DBuilding();
		loadedRooms = fp.getRoomList();
		if (loadedRooms == null || loadedRooms.length == 0) {
			Toast.makeText(this, "Map contains no locations with names", Toast.LENGTH_LONG).show();
			dialog.dismiss();
			return;
		}

		dialog.dismiss();

		if (loadedRooms != null) {
			Toast.makeText(this, "Loaded map data now contains " + loadedRooms.length + " locations with names",
					Toast.LENGTH_LONG).show();
			roomLocation = loadedRooms[(int) (Math.random() * (loadedRooms.length - 1))];
			roomDestination = loadedRooms[(int) (Math.random() * (loadedRooms.length - 1))];
			roomLocation = "D3";
			roomDestination = "D9";
			tvInfo.setText("Random location: " + roomLocation + "\nRandom destination: " + roomDestination);
			button4.setEnabled(true);
		} else {
			tvInfo.setText("Map data contained no rooms!)");
		}
	}

	public void findPath(View v) throws ParserConfigurationException, SAXException, IOException {
		path = fp.getPath(roomLocation, roomDestination, true, false, true);
		if (path != null) {
			tvInfo.setText("Path length = " + path.getTotalDistance());
			paintBoxPath.setCanvasPainter(new Painter());
			// try {
			// blap.addToBuildingFromXMLFile("/mnt/sdcard/footpath_test/e1f2.osm");
			// } catch (FileNotFoundException e) {
			// Log.i("FOOTPATH", "blap file not found!");
			// e.printStackTrace();
			// }
			tlap._a_setNodes(path);
			tlap._b_setBoundaries();
			tlap._c_loadTiles(18);
			button5.setEnabled(true);
		} else {
			tvInfo.setText("Path null");
		}
	}

	public void startNaviagtion(View v) throws FootPathException {
		// fp._a_ is called by pressing the button above
		fp._b_setMovementDetection(FP_MovementDetection.MOVEMENT_DETECTION_STEPS);
		fp._c_setMatchingAlgorithm(FP_MatchingAlgorithm.MATCHING_MULTI_FIT);
		fp._d_setInitialStepLength(.8f);
		fp._e_setLocationProvider(FP_LocationProvider.LOCATION_PROVIDER_FOOTPATH);
		fp._fg_setPath(path);
		fp._h_start();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case CALLED_FOR_FILE_PATH:
			if (resultCode == Activity.RESULT_OK) {
				selectedFilePath = data.getStringExtra("SELECTED_FILE");
				Toast.makeText(this, "Selected map: " + selectedFilePath, Toast.LENGTH_LONG).show();
				button3.setEnabled(true);
			} else {
				Toast.makeText(this, "No file selected!", Toast.LENGTH_LONG).show();
			}
			break;
		}
	}

	private float pixelsPerMeter = 4;

	private IndoorLocation center = new IndoorLocation("center");

	private class Painter implements DrawToCanvasWrapper {
		@Override
		public void drawToCanvas(Canvas canvas) {
			if (path.size() == 0) {
				return;
			}

			Rect bb = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
			Paint black = new Paint();
			canvas.drawRect(bb, black);

			Paint pLine = new Paint();
			Paint pDots = new Paint();
			pLine.setColor(Color.RED);
			pDots.setColor(Color.GREEN);

			Paint pPos = new Paint();
			pPos.setColor(Color.BLUE);

			Paint pTree = new Paint();
			pTree.setColor(Color.YELLOW);

			Paint pLocation = new Paint();
			pLocation.setColor(Color.WHITE);

			MatchingAlgorithm matchingAlgo = fp._debug_getMatchinAlgorithm();
			if (matchingAlgo != null) {
				IndoorLocation algoLoc = matchingAlgo.getLocation();

				if (algoLoc != null) {

					center.setLatitude(algoLoc.getLatitude() + panLocation.getLatitude());
					center.setLongitude(algoLoc.getLongitude() + panLocation.getLongitude());

					tlap.drawToCanvas(canvas, center, canvas.getWidth() / 2, canvas.getHeight() / 2, pixelsPerMeter);
					o2db.setCurrent_level(algoLoc.getLevel());
					o2db.drawToCanvas(canvas, center, canvas.getWidth() / 2, canvas.getHeight() / 2, pixelsPerMeter);
					path.drawToCanvas(canvas, center, canvas.getWidth() / 2, canvas.getHeight() / 2, pixelsPerMeter);

					pLine.setColor(Color.GREEN);
					matchingAlgo._debug_getLocHist().drawToCanvas(canvas, center, canvas.getWidth() / 2,
							canvas.getHeight() / 2, pixelsPerMeter);
					if (matchingAlgo instanceof MultiFit) {
						((MultiFit) matchingAlgo).drawToCanvas(canvas, center, canvas.getWidth() / 2,
								canvas.getHeight() / 2, pixelsPerMeter);
					}

					matchingAlgo.getLocation().drawToCanvas(canvas, center, canvas.getWidth() / 2,
							canvas.getHeight() / 2, pixelsPerMeter);
				}
			}

			// if (temp!=null){
			// int[] xy = convertToPixelLocation(temp.getLocation(), path.getLast(), pixelsPerMeter);
			// canvas.drawCircle(xy[0],xy[1], 8, pDots);
			// }

		}

	}

	private IndoorLocation panLocation = new IndoorLocation("pan");
	private final IndoorLocation zeroCenter = new IndoorLocation("zero");

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		p.onTouch(v, event);
		PanZoomResult res = p.getPanZoomResult();

		switch (res.type) {
		case NONE:
			break;
		case PAN:
			IndoorLocation temp = GeoUtils.convertPixelToGPSLocation(res.x * -1f, res.y, zeroCenter, pixelsPerMeter);
			panLocation.setLatitude(panLocation.getLatitude() + temp.getLatitude());
			panLocation.setLongitude(panLocation.getLongitude() + temp.getLongitude());
			break;
		case ZOOM:
			pixelsPerMeter *= res.scale;
			// Log.i("FOOTPATH", "Setting pixelsPerMeter to " + pixelsPerMeter);
			break;
		}
		return true;
	}
}
