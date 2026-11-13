package com.example.th9;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.adapter.ClassAdapter;
import com.example.th9.adapter.SessionAdapter;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.example.th9.ui.AttendanceStatsActivity;
import com.example.th9.ui.ClassDetailActivity;
import com.example.th9.ui.ClassFormActivity;
import com.example.th9.ui.ScanActivity;
import com.example.th9.ui.SessionDetailActivity;
import com.example.th9.ui.SessionFormActivity;
import com.example.th9.ui.SettingsActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_INITIAL_TAB = "extra_initial_tab";

    private Toolbar toolbar;
    private BottomNavigationView bottomNav;

    // View containers for tabs
    private View viewHome, viewManagement, viewClasses, viewHistory, viewPersonal;
    private boolean showingClassManagement;
    private androidx.activity.OnBackPressedCallback managementBack;

    // Home components
    private MaterialCardView cardActiveSession, cardNoActiveSession;
    private TextView tvHomeActiveTitle, tvHomeActiveClass, tvHomeActiveTime, tvHomeProgressLabel, tvHomeBreakdownLabel;
    private LinearProgressIndicator progressHomeActive;
    private MaterialButton btnHomeContinueScan, btnHomeViewSession, btnHomeEmptyCreateSession;
    private RecyclerView rvHomeRecent;
    private SessionAdapter homeRecentAdapter;
    private TextView tvHomeViewHistory;

    // Classes components
    private EditText etClassesSearch;
    private MaterialButton btnClassesAdd, btnClassesEmptyAdd;
    private RecyclerView rvClasses;
    private ClassAdapter classAdapter;
    private LinearLayout llClassesEmpty;
    private TextView tvClassesNoSearch;

    // History components
    private ChipGroup chipGroupHistoryClass;
    private RecyclerView rvHistory;
    private SessionAdapter historyAdapter;
    private LinearLayout llHistoryEmptyFilter;
    private MaterialButton btnHistoryResetFilter;
    private MaterialCardView cardHistoryStatsBanner;

    private int currentTabId = R.id.nav_home;
    private com.example.th9.ui.SessionFilters filters;
    private boolean classSortDescending;
    private com.example.th9.ui.HomeScheduleSuggestions homeSuggestions;
    private final android.os.Handler homeClock=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable homeTick=new Runnable(){
        @Override public void run(){
            if(AttendanceRepository.getInstance().currentAccount()==null)return;
            refreshHomeData();scheduleHomeTick();
        }
    };
    private void scheduleHomeTick(){homeClock.removeCallbacks(homeTick);if(homeSuggestions!=null)homeClock.postDelayed(homeTick,60000-System.currentTimeMillis()%60000);}
    @Override protected void onPause(){homeClock.removeCallbacks(homeTick);super.onPause();}

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_main);

        initViews();
        setupToolbar();
        setupManagementTab();
        setupPersonalTab();
        if(!AttendanceRepository.getInstance().isAdmin()){bottomNav.getMenu().clear();bottomNav.inflateMenu(R.menu.bottom_nav_teacher);btnClassesAdd.setVisibility(View.GONE);btnClassesEmptyAdd.setVisibility(View.GONE);((TextView)findViewById(R.id.classes_empty_title)).setText("Chưa có lớp được phân công");((TextView)findViewById(R.id.classes_empty_description)).setText("Liên hệ quản trị viên để được phân công lớp trên thiết bị này.");}
        com.example.th9.model.Account account=AttendanceRepository.getInstance().currentAccount();toolbar.setSubtitle((account.isAdmin()?"Quản trị viên":"Giảng viên")+" • "+account.fullName);
        if(AttendanceRepository.getInstance().isAdmin())bottomNav.getMenu().removeItem(R.id.nav_home);
        setupBottomNav();
        if(!AttendanceRepository.getInstance().isAdmin())setupHomeTab();
        setupClassesTab();
        filters=new com.example.th9.ui.SessionFilters(this,this::refreshHistoryData);
        filters.restore(savedInstanceState);
        setupHistoryTab();

        int defaultTab=AttendanceRepository.getInstance().isAdmin()?R.id.nav_classes:R.id.nav_home;
        int requested=savedInstanceState!=null?savedInstanceState.getInt("current_tab",defaultTab)
            :getIntent().getIntExtra(EXTRA_INITIAL_TAB,defaultTab);
        boolean restoreHistory=requested==R.id.nav_history;
        if(restoreHistory)requested=R.id.nav_personal;
        if(bottomNav.getMenu().findItem(requested)==null)requested=defaultTab;
        bottomNav.setSelectedItemId(requested);showTab(requested);
        if(restoreHistory)openHistory();
        if(savedInstanceState!=null&&requested==R.id.nav_classes&&savedInstanceState.getBoolean("class_management")&&AttendanceRepository.getInstance().isAdmin())openClassManagement();

    }

    @Override
    protected void onDataResume() {

        // Refresh display models from the latest committed Room snapshot.
        refreshHomeData();
        refreshClassesData(etClassesSearch != null ? etClassesSearch.getText().toString() : "");
        refreshHistoryData();
        if(filters!=null)filters.classes(chipGroupHistoryClass);
        refreshPersonalData();
        scheduleHomeTick();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_tab", currentTabId);
        outState.putBoolean("class_management",showingClassManagement);
        if(filters!=null)filters.save(outState);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_main);
        bottomNav = findViewById(R.id.bottom_navigation);

        viewHome = findViewById(R.id.view_home);
        viewManagement = findViewById(R.id.view_management);
        viewClasses = findViewById(R.id.view_classes);
        viewHistory = findViewById(R.id.view_history);
        viewPersonal = findViewById(R.id.view_personal);

        // Home
        cardActiveSession = findViewById(R.id.card_active_session);
        cardNoActiveSession = findViewById(R.id.card_no_active_session);
        tvHomeActiveTitle = findViewById(R.id.tv_home_active_title);
        tvHomeActiveClass = findViewById(R.id.tv_home_active_class);
        tvHomeActiveTime = findViewById(R.id.tv_home_active_time);
        tvHomeProgressLabel = findViewById(R.id.tv_home_progress_label);
        tvHomeBreakdownLabel = findViewById(R.id.tv_home_breakdown_label);
        progressHomeActive = findViewById(R.id.progress_home_active);
        btnHomeContinueScan = findViewById(R.id.btn_home_continue_scan);
        btnHomeViewSession = findViewById(R.id.btn_home_view_session);
        btnHomeEmptyCreateSession = findViewById(R.id.btn_home_empty_create_session);
        rvHomeRecent = findViewById(R.id.rv_home_recent_sessions);
        tvHomeViewHistory = findViewById(R.id.tv_home_view_history);

        // Classes
        etClassesSearch = findViewById(R.id.et_classes_search);
        btnClassesAdd = findViewById(R.id.btn_classes_add);
        btnClassesEmptyAdd = findViewById(R.id.btn_classes_empty_add);
        rvClasses = findViewById(R.id.rv_classes_list);
        llClassesEmpty = findViewById(R.id.ll_classes_empty);
        tvClassesNoSearch = findViewById(R.id.tv_classes_no_search);

        // History
        chipGroupHistoryClass = findViewById(R.id.chip_group_history_class);
        rvHistory = findViewById(R.id.rv_history_list);
        llHistoryEmptyFilter = findViewById(R.id.ll_history_empty_filter);
        btnHistoryResetFilter = findViewById(R.id.btn_history_reset_filter);
        cardHistoryStatsBanner = findViewById(R.id.card_history_stats_banner);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0,7301,1,"Lịch sử: Lọc thời gian");menu.add(0,7302,2,"Lịch sử: Lọc trạng thái");
        menu.add(0,7303,3,"Sắp xếp lớp A–Z / Z–A");
        return true;
    }
    @Override public boolean onPrepareOptionsMenu(Menu menu){
        for(int id:new int[]{7301,7302})if(menu.findItem(id)!=null)menu.findItem(id).setVisible(currentTabId==R.id.nav_history);
        if(menu.findItem(7303)!=null)menu.findItem(7303).setVisible(viewClasses.getVisibility()==View.VISIBLE);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId()==7301){filters.dateDialog();return true;}
        if(item.getItemId()==7302){filters.statusDialog();return true;}
        if(item.getItemId()==7303){classSortDescending=!classSortDescending;refreshClassesData(etClassesSearch.getText().toString());return true;}
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            if(item.getItemId()==R.id.nav_timetable){startActivity(new Intent(this,com.example.th9.ui.TimetableActivity.class));return false;}
            showTab(item.getItemId());
            return true;
        });
        bottomNav.setOnItemReselectedListener(item->{if(item.getItemId()==R.id.nav_timetable)startActivity(new Intent(this,com.example.th9.ui.TimetableActivity.class));else showTab(item.getItemId());});
    }
    private void setupPersonalTab(){
        findViewById(R.id.personal_history).setOnClickListener(v->openHistory());
        findViewById(R.id.personal_settings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        findViewById(R.id.personal_logout).setOnClickListener(v->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Đăng xuất?").setMessage("Buổi đang mở và dữ liệu được giữ nguyên.")
            .setPositiveButton("Đăng xuất",(d,w)->com.example.th9.data.Tasks.run(this,v,()->{AttendanceRepository.getInstance().logout();return true;},ok->{
                startActivity(new Intent(this,com.example.th9.ui.LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();
            })).setNegativeButton("Hủy",null).show());
        refreshPersonalData();
    }
    private void refreshPersonalData(){
        com.example.th9.model.Account account=AttendanceRepository.getInstance().currentAccount();if(account==null)return;
        ((TextView)findViewById(R.id.personal_name)).setText(account.fullName);
        ((TextView)findViewById(R.id.personal_username)).setText(account.username);
        ((TextView)findViewById(R.id.personal_role)).setText(account.isAdmin()?"Quản trị viên":"Giảng viên");
    }
    private void openHistory(){
        bottomNav.setSelectedItemId(R.id.nav_personal);
        showTab(R.id.nav_history);refreshHistoryData();
    }

    private void setupManagementTab(){
        findViewById(R.id.card_manage_students).setOnClickListener(v->
            startActivity(new Intent(this,com.example.th9.ui.StudentsActivity.class)));
        findViewById(R.id.card_manage_classes).setOnClickListener(v->openClassManagement());
        findViewById(R.id.card_manage_teachers).setOnClickListener(v->startActivity(new Intent(this,com.example.th9.ui.TeacherAccountsActivity.class)));
        managementBack=new androidx.activity.OnBackPressedCallback(false){
            @Override public void handleOnBackPressed(){showTab(currentTabId==R.id.nav_history?R.id.nav_personal:R.id.nav_classes);}
        };
        getOnBackPressedDispatcher().addCallback(this,managementBack);
        toolbar.setNavigationOnClickListener(v->{if(currentTabId==R.id.nav_history)showTab(R.id.nav_personal);else if(showingClassManagement)showTab(R.id.nav_classes);});
    }

    private void openClassManagement(){
        showingClassManagement=AttendanceRepository.getInstance().isAdmin();
        viewManagement.setVisibility(View.GONE);
        viewClasses.setVisibility(View.VISIBLE);
        managementBack.setEnabled(showingClassManagement);
        if(getSupportActionBar()!=null){
            getSupportActionBar().setTitle(AttendanceRepository.getInstance().isAdmin()?"Quản lý lớp học":"Lớp được phân công");
            getSupportActionBar().setDisplayHomeAsUpEnabled(showingClassManagement);
        }
        invalidateOptionsMenu();
    }

    private void showTab(int navId) {
        if(AttendanceRepository.getInstance().isAdmin()&&navId==R.id.nav_home)navId=R.id.nav_classes;
        currentTabId = navId;
        showingClassManagement=false;
        managementBack.setEnabled(false);
        viewHome.setVisibility(navId == R.id.nav_home ? View.VISIBLE : View.GONE);
        viewManagement.setVisibility(navId == R.id.nav_classes ? View.VISIBLE : View.GONE);
        viewClasses.setVisibility(View.GONE);
        viewHistory.setVisibility(navId == R.id.nav_history ? View.VISIBLE : View.GONE);
        viewPersonal.setVisibility(navId == R.id.nav_personal ? View.VISIBLE : View.GONE);
        if(navId==R.id.nav_history)managementBack.setEnabled(true);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(navId==R.id.nav_history);
            if (navId == R.id.nav_home) {
                getSupportActionBar().setTitle(R.string.app_name);
            } else if (navId == R.id.nav_classes) {
                getSupportActionBar().setTitle(R.string.management_title);
            } else if (navId == R.id.nav_history) {
                getSupportActionBar().setTitle(R.string.history_title);
            } else if(navId==R.id.nav_personal){
                getSupportActionBar().setTitle(R.string.nav_personal);
            }
        }
        if(navId==R.id.nav_classes&&!AttendanceRepository.getInstance().isAdmin())openClassManagement();
        if(navId==R.id.nav_personal)refreshPersonalData();
        invalidateOptionsMenu();
    }

    // --- 1. HOME TAB SETUP ---
    private void setupHomeTab() {
        homeSuggestions=new com.example.th9.ui.HomeScheduleSuggestions(this);
        homeRecentAdapter = new SessionAdapter(this, session -> {
            Intent intent = new Intent(this, SessionDetailActivity.class);
            intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.getId());
            startActivity(intent);
        });
        rvHomeRecent.setLayoutManager(new LinearLayoutManager(this));
        rvHomeRecent.setAdapter(homeRecentAdapter);

        btnHomeContinueScan.setOnClickListener(v -> {
            AttendanceSession active = AttendanceRepository.getInstance().getActiveSession();
            if (active != null) {
                Intent intent = new Intent(this, ScanActivity.class);
                intent.putExtra(ScanActivity.EXTRA_SESSION_ID, active.getId());
                intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_ATTENDANCE);
                startActivity(intent);
            }
        });

        btnHomeViewSession.setOnClickListener(v -> {
            AttendanceSession active = AttendanceRepository.getInstance().getActiveSession();
            if (active != null) {
                Intent intent = new Intent(this, SessionDetailActivity.class);
                intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, active.getId());
                startActivity(intent);
            }
        });

        View.OnClickListener createSessionListener = v -> {
            if(AttendanceRepository.getInstance().getAllClasses().isEmpty()){
                if(AttendanceRepository.getInstance().isAdmin())startActivity(new Intent(this,ClassFormActivity.class));
                else new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("Bạn chưa được phân công lớp. Liên hệ quản trị viên.").setPositiveButton("Đóng",null).show();return;
            }
            Intent intent = new Intent(this, SessionFormActivity.class);
            startActivity(intent);
        };
        btnHomeEmptyCreateSession.setOnClickListener(createSessionListener);

        tvHomeViewHistory.setOnClickListener(v -> {
            openHistory();
        });

        refreshHomeData();
    }

    private void refreshHomeData() {
        if(AttendanceRepository.getInstance().isAdmin())return;
        int suggested=homeSuggestions==null?0:homeSuggestions.render(System.currentTimeMillis());
        AttendanceSession active = AttendanceRepository.getInstance().getActiveSession();
        if (active != null) {
            cardActiveSession.setVisibility(View.VISIBLE);
            cardNoActiveSession.setVisibility(View.GONE);

            tvHomeActiveTitle.setText(active.getTitle());
            ClassRoom cr = AttendanceRepository.getInstance().getClassById(active.getClassId());
            if (cr != null) {
                tvHomeActiveClass.setText(AttendanceRepository.getInstance().sessionClassLabel(active));
            }
            tvHomeActiveTime.setText(active.getStartTime() + " • " + active.getDate()+"\n"+active.getTeacherNameSnapshot());

            int present = AttendanceRepository.getInstance().getPresentCount(active.getId());
            int late = AttendanceRepository.getInstance().getLateCount(active.getId());
            int unchecked = AttendanceRepository.getInstance().getUncheckedCount(active.getId());
            int checkedIn = present + late;
            int total = active.getExpectedCount();
            int pct = total > 0 ? (checkedIn * 100 / total) : 0;

            tvHomeProgressLabel.setText(checkedIn + "/" + total + " đã điểm danh (" + pct + "%)");
            tvHomeBreakdownLabel.setText(present + " Có mặt • " + late + " Đi muộn • " + unchecked + " Chưa điểm danh");
            progressHomeActive.setProgress(pct);
        } else {
            cardActiveSession.setVisibility(View.GONE);
            cardNoActiveSession.setVisibility(suggested>0?View.GONE:View.VISIBLE);
        }
        btnHomeEmptyCreateSession.setText(AttendanceRepository.getInstance().getAllClasses().isEmpty()?(AttendanceRepository.getInstance().isAdmin()?"Tạo lớp":"Chưa được phân công lớp"):"Tạo buổi ngoài lịch");

        // Recent sessions (finished sessions)
        List<AttendanceSession> all = AttendanceRepository.getInstance().getAllSessions();
        List<AttendanceSession> recent = new ArrayList<>();
        for (AttendanceSession s : all) {
            if (s.getStatus() == AttendanceSession.SessionStatus.CLOSED) {
                recent.add(s);
                if (recent.size() >= 2) break;
            }
        }
        homeRecentAdapter.setSessions(recent);
    }

    // --- 2. CLASSES TAB SETUP ---
    private void setupClassesTab() {
        classAdapter = new ClassAdapter(this, classRoom -> {
            Intent intent = new Intent(this, ClassDetailActivity.class);
            intent.putExtra(ClassDetailActivity.EXTRA_CLASS_ID, classRoom.getId());
            startActivity(intent);
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        rvClasses.setAdapter(classAdapter);

        View.OnClickListener addClassListener = v -> {
            Intent intent = new Intent(this, ClassFormActivity.class);
            startActivity(intent);
        };
        btnClassesAdd.setOnClickListener(addClassListener);
        btnClassesEmptyAdd.setOnClickListener(addClassListener);

        etClassesSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshClassesData(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        refreshClassesData("");
    }

    private void refreshClassesData(String query) {
        List<ClassRoom> all = AttendanceRepository.getInstance().getAllClasses();
        if (all.isEmpty()) {
            llClassesEmpty.setVisibility(View.VISIBLE);
            rvClasses.setVisibility(View.GONE);
            tvClassesNoSearch.setVisibility(View.GONE);
            return;
        }

        llClassesEmpty.setVisibility(View.GONE);
        List<ClassRoom> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";

        for (ClassRoom c : all) {
            if (q.isEmpty() || c.getName().toLowerCase().contains(q) || c.getCode().toLowerCase().contains(q)) {
                filtered.add(c);
            }
        }

        if (filtered.isEmpty()) {
            tvClassesNoSearch.setVisibility(View.VISIBLE);
            rvClasses.setVisibility(View.GONE);
        } else {
            tvClassesNoSearch.setVisibility(View.GONE);
            rvClasses.setVisibility(View.VISIBLE);
            filtered.sort((a,b)->classSortDescending?b.getName().compareToIgnoreCase(a.getName()):a.getName().compareToIgnoreCase(b.getName()));
            classAdapter.setClasses(filtered);
        }
    }

    // --- 3. HISTORY TAB SETUP ---
    private void setupHistoryTab() {
        historyAdapter = new SessionAdapter(this, session -> {
            Intent intent = new Intent(this, SessionDetailActivity.class);
            intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.getId());
            startActivity(intent);
        });
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(historyAdapter);

        cardHistoryStatsBanner.setOnClickListener(v -> {
            Intent intent = new Intent(this, AttendanceStatsActivity.class);
            Bundle selection=new Bundle();filters.save(selection);intent.putExtra("filters",selection);
            startActivity(intent);
        });

        filters.classes(chipGroupHistoryClass);

        btnHistoryResetFilter.setOnClickListener(v -> {
            filters.classId=null;filters.from=null;filters.until=null;filters.status=null;filters.classes(chipGroupHistoryClass);
            refreshHistoryData();
        });

        refreshHistoryData();
    }

    private void refreshHistoryData() {
        List<AttendanceSession> all = AttendanceRepository.getInstance().getAllSessions();

        List<AttendanceSession> filtered = new ArrayList<>();
        for (AttendanceSession s : all) {
            if (filters==null||filters.matches(s)) {
                filtered.add(s);
            }
        }

        if (filtered.isEmpty()) {
            llHistoryEmptyFilter.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            llHistoryEmptyFilter.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
            historyAdapter.setSessions(filtered);
        }
    }
}
