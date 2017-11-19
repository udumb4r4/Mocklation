package de.p72b.mocklation.map;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;

import de.p72b.mocklation.R;
import de.p72b.mocklation.dialog.EditLocationItemDialog;
import de.p72b.mocklation.service.geocoder.Constants;
import de.p72b.mocklation.service.geocoder.GeocoderIntentService;
import de.p72b.mocklation.service.permission.IPermissionService;
import de.p72b.mocklation.service.room.LocationItem;
import de.p72b.mocklation.service.setting.ISetting;
import de.p72b.mocklation.util.AppUtil;
import io.reactivex.disposables.CompositeDisposable;

public class MapsPresenter implements IMapsPresenter {

    private static final String TAG = MapsPresenter.class.getSimpleName();
    private final IPermissionService mPermissionService;
    private final boolean mIsLargeLayout;
    private IMapsView mView;
    private FragmentActivity mActivity;
    private Pair<String, LocationItem> mOnTheMapItemPair;
    private CompositeDisposable mDisposables = new CompositeDisposable();
    private boolean mAddressRequested;
    private Address mAddressOutput;
    private AddressResultReceiver mResultReceiver;

    MapsPresenter(FragmentActivity activity, IPermissionService permissionService, ISetting setting) {
        Log.d(TAG, "new MapsPresenter");
        mActivity = activity;
        mView = (IMapsView) activity;
        mPermissionService = permissionService;
        mAddressRequested = false;
        mAddressOutput = null;
        mResultReceiver = new AddressResultReceiver(new Handler());
        mIsLargeLayout = mActivity.getResources().getBoolean(R.bool.large_layout);

        updateUIWidgets();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mDisposables.clear();
    }

    @Override
    public void onMapLongClicked(LatLng latLng) {
        LatLng roundedLatLng = AppUtil.roundLatLng(latLng);
        Log.d(TAG, "onMapLongClicked LatLng: " + roundedLatLng.latitude + " / " + roundedLatLng.longitude);

        String code = AppUtil.createLocationItemCode(roundedLatLng);
        String geoJson = "{'type':'Feature','properties':{},'geometry':{'type':'Point','coordinates':[" + roundedLatLng.longitude + "," + roundedLatLng.latitude + "]}}";
        LocationItem item = new LocationItem(code, code, geoJson, 6, 0);
        mOnTheMapItemPair = new Pair<>(code, item);

        resolveAddressFromLocation(latLng);

        mView.selectLocation(roundedLatLng, code, -1);
    }

    @Override
    public void onMarkerClicked(Marker marker) {
        Log.d(TAG, "onMarkerClicked marker id: " + marker.getId());
    }

    @Override
    public void setLastKnownLocation(Location location) {
        Log.d(TAG, "setLastKnownLocation location:" + location.getProvider() + " "
                + location.getLatitude() + " / " + location.getLongitude() + " isMocked: "
                + location.isFromMockProvider());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.save:
                if (mOnTheMapItemPair == null) {
                    mView.showSnackbar(R.string.error_1002, -1, null, Snackbar.LENGTH_LONG);
                    return;
                }

                showEditLocationItemDialog();
                break;
            case R.id.location:
                mView.showMyLocation();
                break;
            default:
                // do nothing;
        }
    }

    private void showEditLocationItemDialog() {
        FragmentManager fragmentManager = mActivity.getSupportFragmentManager();
        EditLocationItemDialog dialog = EditLocationItemDialog.newInstance(
                new EditLocationItemDialog.EditLocationItemDialogListener() {
                    @Override
                    public void onPositiveClick(LocationItem item) {
                        mActivity.finish();
                    }
                }, mOnTheMapItemPair.second
        );
        dialog.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogFragmentTheme);
        dialog.show(fragmentManager, EditLocationItemDialog.TAG);
    }

    @Override
    public void onMapReady() {
    }

    @Override
    public void removeMarker() {
        mOnTheMapItemPair = null;
    }

    private void resolveAddressFromLocation(@Nullable LatLng latLng) {
        if (!Geocoder.isPresent()) {
            mView.showSnackbar(R.string.error_1007, -1, null, Snackbar.LENGTH_LONG);
            return;
        }

        if (mAddressRequested) {
            return;
        }

        if (latLng == null) {
            return;
        }

        Location location = new Location("");
        location.setLatitude(latLng.latitude);
        location.setLongitude(latLng.longitude);
        startGeocoderIntentService(location);
    }

    private void startGeocoderIntentService(@NonNull Location location) {
        mAddressRequested = true;
        updateUIWidgets();

        Intent intent = new Intent(mActivity, GeocoderIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, location);

        mActivity.getApplication().startService(intent);
    }

    private void updateUIWidgets() {
        mView.setAddressProgressbarVisibility(mAddressRequested ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    private void closeEditLocationItemDialog() {
        EditLocationItemDialog dialogFragment = EditLocationItemDialog.findOnStack(mActivity.getSupportFragmentManager());
        if (dialogFragment != null) {
            dialogFragment.dismiss();
        }
    }

    private class AddressResultReceiver extends ResultReceiver {
        AddressResultReceiver(Handler handler) {
            super(handler);
        }

        /**
         * Receives data sent from FetchAddressIntentService and updates the UI in MainActivity.
         */
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mAddressOutput = resultData.getParcelable(Constants.RESULT_DATA_KEY);
            String resultMessage;
            if (resultCode == Constants.FAILURE_RESULT) {
                resultMessage = resultData.getString(Constants.RESULT_DATA_MESSAGE);
            } else {
                resultMessage = getFormattedAddress(mAddressOutput);
            }
            mView.setAddress(resultMessage);

            mAddressRequested = false;
            updateUIWidgets();
        }

        private String getFormattedAddress(Address address) {
            ArrayList<String> addressFragments = new ArrayList<>();

            for(int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                addressFragments.add(address.getAddressLine(i));
            }
            return TextUtils.join(System.getProperty("line.separator"), addressFragments);
        }
    }
}
