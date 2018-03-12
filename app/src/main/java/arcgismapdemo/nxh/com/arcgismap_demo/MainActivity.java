package arcgismapdemo.nxh.com.arcgismap_demo;

import android.app.Activity;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;

import java.util.List;
import java.util.concurrent.ExecutionException;


public class MainActivity extends Activity {
    private final String URI_GEOCODE_SERVER = "http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer";
    private final String TAG = MainActivity.class.getSimpleName();
    private final String COLUMN_NAME_ADDRESS = "address";
    private final String[] mColumnNames = {BaseColumns._ID, COLUMN_NAME_ADDRESS};
    private SearchView mAddressSearchView;

    private MapView mMapView;
    private LocatorTask mLocatorTask;
    private GraphicsOverlay mGraphicsOverlay;
    private GeocodeParameters mAddressGeocodeParameters;
    private PictureMarkerSymbol mPinSourceSymbol;
    private Callout mCallout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // inflate address search view
        mAddressSearchView = (SearchView) findViewById(R.id.addressSearchView);
        mAddressSearchView.setIconified(false);
        mAddressSearchView.setFocusable(false);
        mAddressSearchView.setQueryHint(getResources().getString(R.string.search_hint));
        // inflate MapView from layout
        mMapView = (MapView) findViewById(R.id.mapView);
//      define pin
        BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        try {
            mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.i(TAG, "Failed to load pin! " + e.getMessage());
        }

        // create a LocatorTask from an online service
        mLocatorTask = new LocatorTask(URI_GEOCODE_SERVER);

        final ArcGISMap map = new ArcGISMap(Basemap.createStreetsVector());
        mMapView.setMap(map);
        // set the map viewpoint to start over North America
        mMapView.setViewpoint(new Viewpoint(40, -100, 100000000));

        // add listener to handle screen taps
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                return true;
            }
        });

        // define the graphics overlay
        mGraphicsOverlay = new GraphicsOverlay();
        setupAddressSearchView();
    }

    private void setupAddressSearchView() {

        mAddressGeocodeParameters = new GeocodeParameters();
        // get place name and address attributes
        mAddressGeocodeParameters.getResultAttributeNames().add("PlaceName");
        mAddressGeocodeParameters.setMaxResults(1);
        mAddressSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String address) {
                geoCodeTypedAddress(address);
                // clear focus from search views
                mAddressSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!newText.equals("")) {
                    final ListenableFuture<List<SuggestResult>> task = mLocatorTask.suggestAsync(newText);
                    task.addDoneListener(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                // get the results of the async operation
                                List<SuggestResult> suggestResults = task.get();
                                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                                int key = 0;
                                // add each address suggestion to a new row
                                for (SuggestResult result : suggestResults) {
                                    suggestionsCursor.addRow(new Object[]{key++, result.getLabel()});
                                }
                                // define SimpleCursorAdapter
                                String[] cols = new String[]{COLUMN_NAME_ADDRESS};
                                int[] to = new int[]{R.id.suggestion_address};
                                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(MainActivity.this,
                                        R.layout.layout_suggestion, suggestionsCursor, cols, to, 0);
                                mAddressSearchView.setSuggestionsAdapter(suggestionAdapter);
                                // handle an address suggestion being chosen
                                mAddressSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                                    @Override
                                    public boolean onSuggestionSelect(int position) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onSuggestionClick(int position) {
                                        // get the selected row
                                        MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                        // get the row's index
                                        int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                        // get the string from the row at index
                                        String address = selectedRow.getString(selectedCursorIndex);
                                        // use clicked suggestion as query
                                        mAddressSearchView.setQuery(address, true);
                                        return true;
                                    }
                                });
                            } catch (Exception e) {
                                Log.i(TAG, e.getMessage());
                            }
                        }
                    });
                }
                return true;
            }
        });
    }

    private void geoCodeTypedAddress(final String address) {
        if (address != null) {
            // Execute async task to find the address
            mLocatorTask.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                        // Call geocodeAsync passing in an address
                        final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                                .geocodeAsync(address, mAddressGeocodeParameters);
                        geocodeResultListenableFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // Get the results of the async operation
                                    List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                                    if (geocodeResults.size() > 0) {
                                        displaySearchResult(geocodeResults.get(0));
                                    } else {
                                        Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                                                Toast.LENGTH_LONG).show();
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    Log.e(TAG, getString(R.string.geo_locate_error));
                                }
                            }
                        });
                    } else {
                        mLocatorTask.retryLoadAsync();
                    }
                }
            });
            mLocatorTask.loadAsync();
        }
    }

    private void displaySearchResult(GeocodeResult geocodeResult) {
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }
        // clear map of existing graphics
        mMapView.getGraphicsOverlays().clear();
        mGraphicsOverlay.getGraphics().clear();
        // create graphic object for resulting location
        Point resultPoint = geocodeResult.getDisplayLocation();
        Graphic resultLocGraphic = new Graphic(resultPoint, geocodeResult.getAttributes(), mPinSourceSymbol);
        // add graphic to location layer
        mGraphicsOverlay.getGraphics().add(resultLocGraphic);
        // zoom map to result over 3 seconds
        mMapView.setViewpointAsync(new Viewpoint(geocodeResult.getExtent()), 3);
        // set the graphics overlay to the map
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
        showCallout(resultLocGraphic);
    }

    private void showCallout(final Graphic graphic) {
        TextView calloutContent = new TextView(getApplicationContext());
        calloutContent.setTextColor(Color.BLACK);
        calloutContent.setText(graphic.getAttributes().get("PlaceName").toString());
        mCallout = mMapView.getCallout();
        mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
        mCallout.setContent(calloutContent);
        Point calloutLocation = graphic.computeCalloutLocation(graphic.getGeometry().getExtent().getCenter(), mMapView);
        mCallout.setGeoElement(graphic, calloutLocation);
        mCallout.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    }
}
