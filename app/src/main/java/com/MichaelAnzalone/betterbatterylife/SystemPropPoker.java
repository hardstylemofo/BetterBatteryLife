//package com.MichaelAnzalone.betterbatterylife;
//
//import android.content.ContentResolver;
//import android.net.Uri;
//import android.os.AsyncTask;
//import android.os.IBinder;
//import android.os.Parcel;
//import android.util.Log;
//import androidx.annotation.NonNull;
//
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//
//class SystemPropPoker extends AsyncTask<Void, Void, Void> {
//
//    private static final String TAG = SystemPropPoker.class.getName();
//
//    @SuppressWarnings("unchecked")
//    @Override
//    protected Void doInBackground(@NonNull Void... params) {
//        String[] services;
//        try {
//            Class serviceManagerClass = Class.forName("android.os.ServiceManager");
//            Method listServicesMethod = serviceManagerClass.getMethod("listServices");
//            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
//
//            services = (String[]) listServicesMethod.invoke(null);
//            for (String service : services) {
//
//                if(service.equals(new String ("power" )))//deviceidle
//                {
////                    int m = 3;
////                    int k = 2;
//                    ContentResolver dozeService = (ContentResolver) getServiceMethod.invoke(null, service);
//
//                      if(dozeService != null) {
//                            Class dozeClass = dozeService.getClass();
//
////                             String name = dozeClass.getName();
////
////                           // ContentResolver dozeOnChange = (ContentResolver) dozeClass;
////
////                            if(dozeClass != null)
////                            {
////                                dozeClass.getMethod("onChange", boolean.class, Uri.class);
////                            }
//                      }
////                    Method onChangeMethod = serviceManagerClass.getMethod("onChange");
//                    break;
//                }
//
//                Method checkServiceMethod = serviceManagerClass.getMethod("checkService", String.class);
//                IBinder obj = (IBinder) checkServiceMethod.invoke(null, service);
//                if (obj != null) {
//                    Parcel data = Parcel.obtain();
//                    final int SYSPROPS_TRANSACTION = ('_' << 24) | ('S' << 16) | ('P' << 8) | 'R'; //copy from source code in android.os.IBinder.java
//                    try {
//                        obj.transact(SYSPROPS_TRANSACTION, data, null, 0);
//                    } catch (Exception e) {
//                        Log.i(TAG, "Someone wrote a bad service '" + service
//                                + "' that doesn't like to be poked: " + e);
//                    }
//                    data.recycle();
//                }
//            }
//        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//}