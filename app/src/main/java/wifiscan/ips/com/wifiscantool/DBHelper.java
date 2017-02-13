package wifiscan.ips.com.wifiscantool;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private final static int _DBVersion = 1;
    private final static String _DBName = "WifInfo.db";
    public final static String _TableLocation = "WifiLocation";
    public final static String _TableWifi = "WifiFingerprinting";
    public final static String TimeField = "_TIME";

    public DBHelper(Context context) {
        super(context, _DBName, null, _DBVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        final String SQL_Location = "CREATE TABLE IF NOT EXISTS " + _TableLocation + "( " +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                TimeField + " VARCHAR(20), " +
                "_LATITUDE VARCHAR(50), " +
                "_LONGITUDE VARCHAR(50), " +
                "_GPSLAT VARCHAR(50), " +
                "_GPSLONG VARCHAR(50), " +
                "_TEMP REAL, " +
                "_HUMIDITY VARCHAR(5)," +
                "_WINDKPH VARCHAR(5)," +
                "_WINDDIR VARCHAR(10)," +
                "_WINDDEGREE VARCHAR(10)" +
                ");";
        final String SQL_Fingerprint = "CREATE TABLE IF NOT EXISTS " + _TableWifi + "( " +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                TimeField + " VARCHAR(20), " +
                "_SSID VARCHAR(30), " +
                "_BSSID VARCHAR(20)," +
                "_FREQ INTEGER," +
                "_LEVEL INTEGER," +
                "_CONTENT TEXT" +
                //" FOREIGN KEY (" + TimeField + ") REFERENCES " + SQL_Location + "(" + TimeField + ")" +
                ");";
        db.execSQL(SQL_Location);
        db.execSQL(SQL_Fingerprint);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        final String SQL_Location = "DROP TABLE " + _TableLocation;
        final String SQL_Fingerprint = "DROP TABLE " + _TableWifi;
        db.execSQL(SQL_Location);
        db.execSQL(SQL_Fingerprint);
    }
}
