package com.qinbaobao.photoslide;

// MainActivity.java

import android.graphics.Color;
import android.os.*;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private FrameLayout container;
    private boolean isCharging = false;
    private final List<Uri> imageUris = new ArrayList<>();
    private final BroadcastReceiver chargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_POWER_CONNECTED:
                    isCharging = true;
                    updateLayout();
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    isCharging = false;
                    updateLayout();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        container = findViewById(R.id.container);
        checkPermissions();
    }
    // 监听屏幕配置变化
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayout();
    }

    private void checkPermissions() {
        // 修改权限为 READ_MEDIA_IMAGES（Android 13+）
        if (ActivityCompat.checkSelfPermission(this,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                        android.Manifest.permission.READ_MEDIA_IMAGES :
                        android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                                         android.Manifest.permission.READ_MEDIA_IMAGES :
                                         android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    1);
        } else {
            loadImages();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadImages();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage("需要存储权限来读取照片")
                    .setPositiveButton("确定", (dialog, which) -> finish())
                    .show();
        }
    }

    private void loadImages() {
        String[] projection = {MediaStore.Images.Media._ID};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");

            if (cursor != null) {
                imageUris.clear();
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    imageUris.add(uri);
                }

                // 添加随机排序（仅影响轮播模式）
                Collections.shuffle(imageUris);
                updateLayout();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void updateLayout() {
        Log.d("LayoutUpdate", "横屏状态: " + (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                                      + " 充电状态: " + isCharging);
        container.removeAllViews();
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape && isCharging) {
            setupSlideshow();
        } else {
            setupGrid();
        }
    }

    private void setupGrid() {
        // 取消屏幕常亮
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 显示状态栏（使用clearFlags保持兼容性）
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        container.setBackgroundColor(Color.WHITE);

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(new GridAdapter());
        container.addView(recyclerView);
    }

    private void setupSlideshow() {
        // 添加屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 隐藏状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        container.setBackgroundColor(Color.BLACK);

        ViewPager2 viewPager = new ViewPager2(this);
        viewPager.setAdapter(new PagerAdapter());
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(viewPager);

        // 修改页面切换动画
        viewPager.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(@NonNull View page, float position) {
                page.setAlpha(0f);
                page.setTranslationZ(-Math.abs(position)); // 防止重叠渲染

                // 交叉淡入淡出动画
                if (position >= -1 && position <= 1) {
                    float alpha = 1 - Math.abs(position); // 中间位置alpha=1，边缘alpha=0
                    page.setAlpha(alpha);
                    page.setTranslationX(page.getWidth() * -position); // 保持默认水平位移
                }

                // 添加动画时长和插值器
                page.animate()
                        .setDuration(5000)
                        .setInterpolator(new LinearInterpolator())
                        .start();
            }
        });

        startAutoScroll(viewPager);
    }

    private void startAutoScroll(ViewPager2 viewPager) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int nextItem = (viewPager.getCurrentItem() + 1) % imageUris.size();
                viewPager.setCurrentItem(nextItem, true);
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(runnable, 3000);

        viewPager.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                handler.removeCallbacks(runnable);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 添加初始充电状态检查
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;

        registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));
        updateLayout(); // 添加这行立即更新布局
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(chargingReceiver);
    }

    // Grid Adapter
    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.grid_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Glide.with(holder.itemView)
                    .load(imageUris.get(position))
                    .override(200, 200)
                    .centerCrop()
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imageUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.image_item);
            }
        }
    }

    // Pager Adapter
    private class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.pager_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Glide.with(holder.itemView)
                    .load(imageUris.get(position))
                    .fitCenter()
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return imageUris.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.pager_image);
            }
        }
    }
}
