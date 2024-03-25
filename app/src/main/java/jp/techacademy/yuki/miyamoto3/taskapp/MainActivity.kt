package jp.techacademy.yuki.miyamoto3.taskapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.Sort
import jp.techacademy.yuki.miyamoto3.taskapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*

const val EXTRA_TASK = "jp.techacademy.yuki.miyamoto3.taskapp.TASK"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var taskAdapter: TaskAdapter
    private lateinit var realm: Realm
    private var allTaskList = mutableListOf<Task>()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // OSバージョン確認APIレベル33以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 通知権限が許可されているか確認する
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // APIレベル33以前のため、アプリ毎の通知設定を確認する
            if( !NotificationManagerCompat.from(this).areNotificationsEnabled()){
                // OSバージョン確認（APIレベル26以上）
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // APIレベルが26以上なので、直接通知の設定画面に遷移する
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                    }
                } else {
                    // APIレベルが26未満なので、アプリのシステム設定に遷移する
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    )
                }
                // 生成されたインテントにIntent.FLAG_ACTIVITY_NEW_TASKを付加
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        binding.fab.setOnClickListener {
            val intent = Intent(this, InputActivity::class.java)
            startActivity(intent)
        }

        binding.searchButton.setOnClickListener {
            val category = binding.categorySearchText.text.toString()
            taskAdapter.filteringTaskList(allTaskList, category)
        }

        // TaskAdapterを生成し、ListViewに設定する
        taskAdapter = TaskAdapter(this)
        binding.listView.adapter = taskAdapter
        // ListViewをタップしたときの処理
        binding.listView.setOnItemClickListener { parent, _, position, _ ->
            // 入力・編集する画面に遷移させる
            val task = parent.adapter.getItem(position) as Task
            val intent = Intent(this, InputActivity::class.java)
            intent.putExtra(EXTRA_TASK, task.id)
            startActivity(intent)
        }

        // ListViewを長押ししたときの処理
        binding.listView.setOnItemLongClickListener { parent, _, position, _ ->
            // タスクを削除する
            val task = parent.adapter.getItem(position) as Task

            // ダイアログを表示する
            val builder = AlertDialog.Builder(this)

            builder.setTitle("削除")
            builder.setMessage(task.title + "を削除しますか")
            builder.setPositiveButton("OK") { _, _ ->
                realm.writeBlocking {
                    // タスクのIDに該当するデータを削除する
                    val tasks = query<Task>("id==${task.id}").find()
                    tasks.forEach {
                        delete(it)
                    }
                }

                // アラームを削除
                val resultIntent = Intent(applicationContext, TaskAlarmReceiver::class.java)
                resultIntent.putExtra(EXTRA_TASK, task.id)
                val resultPendingIntent = PendingIntent.getBroadcast(
                    this,
                    task.id,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(resultPendingIntent)
            }

            builder.setNegativeButton("CANCEL", null)

            val dialog = builder.create()
            dialog.show()

            true
        }

        // Realmデータベースとの接続を開く
        val config = RealmConfiguration.create(schema = setOf(Task::class))
        realm = Realm.open(config)

        // Realmからタスクの一覧を取得
        val tasks = realm.query<Task>().sort("date", Sort.DESCENDING).find()

        // Realmが起動、または更新（追加、変更、削除）時にreloadListViewを実行する
        CoroutineScope(Dispatchers.Default).launch {
            tasks.asFlow().collect {
                when (it) {
                    // 更新時
                    is UpdatedResults -> reloadListView(it.list)
                    // 起動時
                    is InitialResults -> reloadListView(it.list)
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    /**
     * リストの一覧を更新する
     */
    private suspend fun reloadListView(tasks: List<Task>) {
        allTaskList.addAll(tasks)
        withContext(Dispatchers.Main) {
            taskAdapter.updateTaskList(tasks)
        }
        binding.categorySearchText.setText("")
    }
}