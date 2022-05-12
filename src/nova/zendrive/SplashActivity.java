package nova.zendrive;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.FragmentActivity;

public class SplashActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isUserLoggedIn()) {
            startActivity(new Intent(this, NRCore.class));
        } else {
            startActivity(new Intent(this, NRCore.class));
        }
        finish();
    }

    private boolean isUserLoggedIn(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                this.getApplicationContext());
        String savedDriverId = sharedPreferences.getString(SharedPreferenceManager.DRIVER_ID_KEY, null);
        return savedDriverId!=null && !savedDriverId.equalsIgnoreCase("");
    }
}
