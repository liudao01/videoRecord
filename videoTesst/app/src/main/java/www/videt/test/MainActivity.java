package www.videt.test;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

public class MainActivity extends Activity {
    private Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button1).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {


                Intent intent = new Intent();
                intent.setClass(getApplicationContext(),
                        RecorderVideoActivity.class);
                startActivityForResult(intent, 100);
            }
        });


        button2 = (Button) findViewById(R.id.button2);

        button2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getPermissions();
            }
        });
    }

    private  void getPermissions(){
        XXPermissions.with(this)
                // 可设置被拒绝后继续申请，直到用户授权或者永久拒绝
                //.constantRequest()
                // 支持请求6.0悬浮窗权限8.0请求安装权限
                //.permission(Permission.REQUEST_INSTALL_PACKAGES)
                .permission(Permission.CAMERA)
                .permission(Permission.BODY_SENSORS)
                .permission(Permission.RECORD_AUDIO)
                .permission(Permission.READ_EXTERNAL_STORAGE)
                .permission(Permission.WRITE_EXTERNAL_STORAGE)
                // 不指定权限则自动获取清单中的危险权限
//                .permission(Permission.Group.STORAGE)
                .request(new OnPermission() {

                    @Override
                    public void hasPermission(List<String> granted, boolean all) {
                        if (all) {
//                            ToastUtils.showCenter("获取权限成功");
//                            tv_camera.callOnClick();
                            Toast.makeText(MainActivity.this, "获取权限成功", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void noPermission(List<String> denied, boolean quick) {
                        Toast.makeText(MainActivity.this, "获取权限成功", Toast.LENGTH_SHORT).show();
//                        ToastUtils.showCenter("获取权限失败");
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 100) {
                Uri uri = data.getParcelableExtra("uri");
                String[] projects = new String[]{MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.DURATION};
                Cursor cursor = getContentResolver().query(uri, projects, null,
                        null, null);
                int duration = 0;
                String filePath = null;

                if (cursor.moveToFirst()) {
                    // 路径：MediaStore.Audio.Media.DATA
                    filePath = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                    // 总播放时长：MediaStore.Audio.Media.DURATION
                    duration = cursor
                            .getInt(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                    Toast.makeText(this, "路径 = " + filePath, Toast.LENGTH_SHORT)
                            .show();
                }
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }

            }
        }
    }
}
