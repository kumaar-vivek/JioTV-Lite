package com.example.jiotvservice.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.annotation.NonNull;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import com.example.jiotvservice.util.Logger;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.widget.Toast;
import android.widget.PopupWindow;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import com.example.jiotvservice.R;
import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.api.JioTvApiService;
import com.example.jiotvservice.api.RawResponseReader;
import com.example.jiotvservice.epg.EpgProgram;
import com.example.jiotvservice.epg.EpgRepository;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.player.JioPlayerManager;
import com.example.jiotvservice.session.SessionManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private SessionManager session;
    private PlaybackViewModel viewModel;
    private JioPlayerManager playerManager;
    
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Channel> filteredChannels = new ArrayList<>();
    private int currentIndex = 0;
    private final StringBuilder digits = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService channelExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService prefetchExecutor = Executors.newFixedThreadPool(2);

    private LinearLayout login, gridContainer, overlay;
    private EditText mobile, otp;
    private Button sendOtp, verifyOtp;
    private TextView channelNumber, channelName, errorMessage, tvCurrentDate, tvCurrentTime;
    private GridView grid;
    private PlayerView playerView;
    private TextView filterLanguage, filterCategory;
    private EditText etSearch;
    private ImageButton btnVoiceSearch, btnLogout;
    private String searchQuery = "";
    private static final int REQ_VOICE_SEARCH = 1001;
    private static final int REQ_AUDIO_PERMISSION = 1002;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;

    // EPG / favorites
    private EpgRepository epgRepository;
    private final Map<Integer, View> epgRowViews = new HashMap<>();
    private final List<HorizontalScrollView> epgHorizontalScrolls = new ArrayList<>();
    private long epgGeneration = 0;
    private ViewGroup epgOverlayRoot;
    private LinearLayout epgRowsContainer;
    private LinearLayout epgTimeHeader;
    private HorizontalScrollView epgTimeHeaderScroll;
    private TextView epgDate;
    private TextView epgStatus;
    private ImageButton btnEpg;
    private ImageButton btnClearSearch;
    private LinearLayout playerProgramBanner;
    private ImageView playerProgramLogo;
    private TextView playerProgramChannel;
    private TextView playerProgramTitle;
    private TextView playerProgramCategory;
    private ImageButton playerEpgButton;
    private ImageButton playerFavoriteButton;
    private EpgProgram currentProgram;
    private boolean epgOpenedFromPlayer = false;
    private boolean epgBackFromPlayerGoesToGrid = false;
    private boolean playerProgramBannerVisible = false;
    private static final int EPG_PAGE_SIZE = 100;
    private int epgNextChannelIndex = 0;
    private TextView epgLoadMoreView;
    private PopupWindow iconTooltipPopup;
    private final Handler tooltipHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTooltip;
    private static final long PLAYER_BANNER_AUTO_HIDE_MS = 5000L;
    private final Runnable hidePlayerProgramBanner = () -> {
        if (playerProgramBanner != null) {
            playerProgramBanner.setVisibility(View.GONE);
            playerProgramBannerVisible = false;
        }
    };

    private final String[] catNames = {"All Categories", "Entertainment", "Movies", "Kids", "Sports", "Lifestyle", "Infotainment", "News", "Music", "Devotional", "Business", "Educational", "Shopping", "JioDarshan"};
    private final int[] catIds = {0, 5, 6, 7, 8, 9, 10, 12, 13, 15, 16, 17, 18, 19};
    private final boolean[] catSelected = new boolean[catNames.length];

    private final String[] langNames = {"All Languages", "Hindi", "Marathi", "Punjabi", "Urdu", "Bengali", "English", "Malayalam", "Tamil", "Gujarati", "Odia", "Telugu", "Bhojpuri", "Kannada", "Assamese", "Nepali", "French", "Other"};
    private final int[] langIds = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18};
    private final boolean[] langSelected = new boolean[langNames.length];

    private final Runnable commitDigits = this::commitDigits;
    private final Runnable hideOverlay = () -> overlay.setVisibility(View.GONE);
    
    private final Runnable updateClock = new Runnable() {
        @Override public void run() {
            updateClockDisplay();
            handler.postDelayed(this, 1000);
        }
    };

    @UnstableApi
    @Override protected void onCreate(Bundle state) {
        Logger.d("MYJIO", "MainActivity:onCreate called");
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        
        requestStoragePermissions();

        session = new SessionManager(this);
        playerManager = new JioPlayerManager(this, session);
        // EPG repository must exist before playback starts or the EPG overlay/banner
        // paths will dereference a null repository.
        epgRepository = new EpgRepository(getApplicationContext());
        
        playerManager.setListener(() -> {
            Logger.w("MYJIO", "Playback stall reported by player manager, triggering fallback...");
            if (!viewModel.tryNextFallback()) {
                showPlaybackFailure("Playback error: No fallbacks available");
            }
        });

        viewModel = new ViewModelProvider(this, new PlaybackViewModelFactory(this, session)).get(PlaybackViewModel.class);
        
        initSpeechRecognizer();
        bindViews();
        setupObservers();
        setupLogin();

        if (session.isLoggedIn()) {
            Logger.d("MYJIO", "Active session found, performing startup refresh...");
            showPlayerMode();
            fullRefreshAndFetchChannels();
        } else {
            showLoginMode();
        }
    }

    private void requestStoragePermissions() {
        String[] perms = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        
        boolean needsRequest = false;
        for (String p : perms) {
            if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                needsRequest = true;
                break;
            }
        }
        
        if (needsRequest) {
            requestPermissions(perms, 100);
        }
    }

    @UnstableApi
    private void setupObservers() {
        viewModel.getPlaybackData().observe(this, response -> {
            Channel ch = viewModel.getCurrentChannel().getValue();
            if (ch != null && response != null) {
                showPlayerMode();
                List<String> candidates = viewModel.getCandidates();
                int index = viewModel.getCandidateIndex();
                if (index < candidates.size()) {
                    String url = candidates.get(index);
                    String lic = url.contains(".mpd") ? response.getMpdKey() : null;
                    
                    Logger.i("MYJIO", "PLAYING CHANNEL: " + ch.getChannelName() + 
                            " | Index: " + index + "/" + candidates.size() + 
                            " | Type: " + (url.contains(".mpd") ? "DASH" : "HLS") +
                            " | URL: " + url);
                            
                    setPlaybackKeepScreenOn(true);
                    playerManager.prepare(url, lic, response);
                    showChannelOverlay(ch);
                    if (currentProgram != null) {
                        updatePlayerProgramBanner(ch, currentProgram);
                    } else {
                        loadCurrentProgram(ch);
                    }
                    session.saveLastChannel(ch.getChannelId(), ch.getChannelNumber());
                } else {
                    Logger.e("MYJIO", "Illegal candidate index: " + index);
                }
            }
        });

        viewModel.getError().observe(this, this::showPlaybackFailure);
    }

    private void fullRefreshAndFetchChannels() {
        new Thread(() -> {
            boolean success = ApiProvider.performFullRefresh(this);
            runOnUiThread(() -> {
                if (success) Logger.d("MYJIO", "Startup full refresh successful");
                else Logger.e("MYJIO", "Startup full refresh failed");
                fetchChannelsAndResume(true);
            });
        }).start();
    }

    @UnstableApi
    private void bindViews() {
        login = findViewById(R.id.login_container);
        gridContainer = findViewById(R.id.channel_grid_container);
        overlay = findViewById(R.id.channel_overlay);
        channelNumber = findViewById(R.id.tv_channel_number);
        channelName = findViewById(R.id.tv_channel_name);
        errorMessage = findViewById(R.id.tv_error_message);
        mobile = findViewById(R.id.et_mobile);
        otp = findViewById(R.id.et_otp);
        sendOtp = findViewById(R.id.btn_send_otp);
        verifyOtp = findViewById(R.id.btn_verify_otp);
        grid = findViewById(R.id.channel_grid);
        playerView = findViewById(R.id.player_view);
        playerManager.setPlayerView(playerView);
        tvCurrentDate = findViewById(R.id.tv_current_date);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        filterLanguage = findViewById(R.id.filter_language);
        filterCategory = findViewById(R.id.filter_category);
        etSearch = findViewById(R.id.et_search);
        btnVoiceSearch = findViewById(R.id.btn_voice_search);
        btnLogout = findViewById(R.id.btn_logout);

        btnEpg = findViewById(R.id.btn_epg);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        epgOverlayRoot = findViewById(R.id.epg_overlay_root);
        epgRowsContainer = findViewById(R.id.epg_rows_container);
        epgTimeHeader = findViewById(R.id.epg_time_header);
        epgTimeHeaderScroll = findViewById(R.id.epg_time_header_scroll);
        epgDate = findViewById(R.id.epg_date);
        epgStatus = findViewById(R.id.epg_status);

        playerProgramBanner = findViewById(R.id.player_program_banner);
        playerProgramLogo = findViewById(R.id.player_program_logo);
        playerProgramChannel = findViewById(R.id.player_program_channel);
        playerProgramTitle = findViewById(R.id.player_program_title);
        playerProgramCategory = findViewById(R.id.player_program_category);
        playerEpgButton = findViewById(R.id.player_epg_button);
        if (playerEpgButton != null) playerEpgButton.setColorFilter(Color.rgb(165, 0, 33));
        playerFavoriteButton = findViewById(R.id.player_favorite_button);

        setupSearch();
        setupEpgUi();
        btnLogout.setOnClickListener(v -> performLogout());
        initFilterStates();
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                applyFilters();
                updateSearchClearButton();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnVoiceSearch.setOnClickListener(v -> toggleVoiceSearch());
        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearch.setText("");
                etSearch.requestFocus();
            });
        }
        updateSearchClearButton();
    }

    private void updateSearchClearButton() {
        if (btnClearSearch != null) {
            btnClearSearch.setVisibility(
                    etSearch != null && etSearch.length() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isListening = true;
                btnVoiceSearch.setColorFilter(Color.RED);
                Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { stopListeningUI(); }
            @Override public void onError(int error) {
                stopListeningUI();
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Logger.e("STT", "Error: " + error);
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    etSearch.setText(matches.get(0));
                    etSearch.setSelection(etSearch.getText().length());
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    etSearch.setText(matches.get(0));
                    etSearch.setSelection(etSearch.getText().length());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void toggleVoiceSearch() {
        if (isListening) {
            speechRecognizer.stopListening();
            stopListeningUI();
        } else {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERMISSION);
            } else {
                startVoiceRecognition();
            }
        }
    }

    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening(speechIntent);
        } else {
            // Fallback to intent if SpeechRecognizer is not available
            try { startActivityForResult(speechIntent, REQ_VOICE_SEARCH); }
            catch (Exception e) { Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show(); }
        }
    }

    private void stopListeningUI() {
        isListening = false;
        btnVoiceSearch.clearColorFilter();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO_PERMISSION && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition();
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (gridContainer.getVisibility() == View.VISIBLE) {
            toggleVoiceSearch();
            return true;
        }
        return false;
    }

    private void performLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    session.clearSession();
                    showLoginMode();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_SEARCH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                etSearch.setText(result.get(0));
            }
        }
    }

    private void initFilterStates() {
        for (int i = 0; i < catIds.length; i++) {
            int id = catIds[i];
            if (id == 5 || id == 6 || id == 7 || id == 8 || id == 10 || id == 12 || id == 13) catSelected[i] = true;
        }
        for (int i = 0; i < langIds.length; i++) {
            int id = langIds[i];
            if (id == 1 || id == 6 || id == 12) langSelected[i] = true;
        }
        updateFilterTexts();
        filterLanguage.setOnClickListener(v -> showMultiSelectDialog("Select Languages", langNames, langSelected, this::applyFilters));
        filterCategory.setOnClickListener(v -> showMultiSelectDialog("Select Categories", catNames, catSelected, this::applyFilters));
        setupFilterFocus(filterLanguage);
        setupFilterFocus(filterCategory);
    }

    private void setupFilterFocus(final View filter) {
        filter.setFocusable(true);
        filter.setFocusableInTouchMode(true);
        filter.setOnFocusChangeListener((v, hasFocus) -> v.setSelected(hasFocus));
    }

    private void showMultiSelectDialog(String title, String[] items, boolean[] selected, Runnable onDone) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMultiChoiceItems(items, selected, (dialog, which, isChecked) -> {
            if (which == 0) {
                for (int i = 0; i < selected.length; i++) {
                    selected[i] = isChecked;
                    ((AlertDialog) dialog).getListView().setItemChecked(i, isChecked);
                }
            } else if (!isChecked) {
                selected[0] = false;
                ((AlertDialog) dialog).getListView().setItemChecked(0, false);
            }
        });
        builder.setPositiveButton("OK", (dialog, which) -> { updateFilterTexts(); onDone.run(); });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateFilterTexts() {
        filterLanguage.setText("Language: " + getSelectedSummary(langNames, langSelected));
        filterCategory.setText("Category: " + getSelectedSummary(catNames, catSelected));
    }

    private String getSelectedSummary(String[] names, boolean[] selected) {
        if (selected[0]) return "All";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 1; i < selected.length; i++) {
            if (selected[i]) {
                if (count > 0) sb.append(", ");
                sb.append(names[i]);
                count++;
            }
        }
        if (count == 0) return "None";
        return sb.length() > 30 ? sb.substring(0, 27) + "..." : sb.toString();
    }

    private void updateClockDisplay() {
        Date now = new Date();
        SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyyy", Locale.US);
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss", Locale.US);
        if (tvCurrentDate != null) tvCurrentDate.setText(sdfDate.format(now));
        if (tvCurrentTime != null) tvCurrentTime.setText(sdfTime.format(now));
    }

    private String pendingMobile;

    private void setupLogin() {
        sendOtp.setOnClickListener(v -> {
            String number = AuthModels.normalizeMobile(mobile.getText().toString().trim());
            if (!number.matches("^\\+91[6-9][0-9]{9}$")) {
                Toast.makeText(getApplicationContext(), "Enter valid number", Toast.LENGTH_LONG).show();
                return;
            }
            pendingMobile = number; sendOtp.setEnabled(false);
            ApiProvider.auth().sendOtp(new AuthModels.OtpRequest(pendingMobile)).enqueue(new Callback<AuthModels.OtpSendResponse>() {
                @Override public void onResponse(Call<AuthModels.OtpSendResponse> c, Response<AuthModels.OtpSendResponse> r) {
                    if (!r.isSuccessful()) { sendOtp.setEnabled(true); return; }
                    otp.setVisibility(View.VISIBLE); verifyOtp.setVisibility(View.VISIBLE); sendOtp.setVisibility(View.GONE);
                }
                @Override public void onFailure(Call<AuthModels.OtpSendResponse> c, Throwable t) { sendOtp.setEnabled(true); }
            });
        });

        verifyOtp.setOnClickListener(v -> {
            String code=otp.getText().toString().trim();
            if(pendingMobile==null||code.isEmpty()){return;}
            verifyOtp.setEnabled(false);
            ApiProvider.auth().verifyOtp(new AuthModels.OtpVerifyRequest(pendingMobile,code,DeviceInfoFactory.create(this))).enqueue(new Callback<AuthModels.AuthResponse>() {
                @Override public void onResponse(Call<AuthModels.AuthResponse> c, Response<AuthModels.AuthResponse> r) {
                    if(!r.isSuccessful()){verifyOtp.setEnabled(true);return;}
                    AuthModels.AuthResponse body=r.body();
                    if(body!=null && body.hasToken()){
                        session.saveAuthSession(body.getAccessToken(),body.getSsoToken(),body.getRefreshToken(),body.getUniqueId(),pendingMobile);
                        session.saveSubscriberId(body.getSubscriberId());
                        session.saveLbCookie(body.getLbCookie());
                        showGridMode(); fetchChannelsAndResume(false);
                    }
                }
                @Override public void onFailure(Call<AuthModels.AuthResponse> c, Throwable t){verifyOtp.setEnabled(true);}
            });
        });
    }

    private void fetchChannelsAndResume(boolean resumeFromHistory) {
        if (!session.isLoggedIn()) { showLoginMode(); return; }
        String subscriberId = session.getSubscriberId();
        ApiProvider.get().getChannels(JioTvApiService.CHANNELS_ALT_URL, session.getSsoToken(), session.getAccessToken(), subscriberId, subscriberId, subscriberId, session.getUniqueId(), "tvYR7NSNn7rymo3F")
            .enqueue(new Callback<ResponseBody>() {
                @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                    if (!r.isSuccessful()) { showChannelLoadFailure("Failed to load channels", false); return; }
                    readAndParseChannelsInBackground(r.body(), resumeFromHistory);
                }
                @Override public void onFailure(Call<ResponseBody> c, Throwable t) { showChannelLoadFailure("Failed to load channels", false); }
            });
    }

    private void readAndParseChannelsInBackground(ResponseBody responseBody, boolean resumeFromHistory) {
        channelExecutor.execute(() -> {
            try {
                String raw = RawResponseReader.readChunked(responseBody).getBody();
                AuthModels.ChannelListResponse body = AuthModels.ChannelListResponse.fromRawJson(raw);
                if (body != null && body.getResult() != null) {
                    runOnUiThread(() -> {
                        allChannels.clear(); 
                        allChannels.addAll(body.getResult());
                        Collections.sort(allChannels, (c1, c2) -> {
                            return Integer.compare(c1.getChannelNumber(), c2.getChannelNumber());
                        });
                        applyFilters();
                        int historyIndex = findFilteredIndexById(session.getLastChannelId());
                        if (resumeFromHistory && historyIndex >= 0) {
                            currentIndex = historyIndex; showPlayerMode(); playSelectedChannel(filteredChannels.get(currentIndex));
                        } else showGridMode();
                    });
                }
            } catch (Exception e) { Logger.e("MYJIO", "Parse error", e); }
        });
    }

    private void applyFilters() {
        filteredChannels.clear();
        for (Channel c : allChannels) {
            if (matchesLanguage(c) && matchesCategory(c) && matchesSearch(c)) {
                filteredChannels.add(c);
            }
        }

        // Favorites first; within both groups keep ascending user-visible
        // channel number, matching the requested JioTV-style ordering.
        Collections.sort(filteredChannels, (a, b) -> {
            boolean af = session != null && session.isFavoriteChannel(a.getChannelId());
            boolean bf = session != null && session.isFavoriteChannel(b.getChannelId());
            if (af != bf) return af ? -1 : 1;
            int n = Integer.compare(a.getChannelNumber(), b.getChannelNumber());
            if (n != 0) return n;
            return a.getChannelName().compareToIgnoreCase(b.getChannelName());
        });

        setupGrid();
    }

    private boolean matchesSearch(Channel c) {
        if (searchQuery == null || searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase().trim();
        return c.getChannelName().toLowerCase().contains(q) || 
               String.valueOf(c.getChannelNumber()).contains(q);
    }

    private boolean matchesLanguage(Channel c) {
        if (langSelected[0]) return true;
        int id = c.getChannelLanguageId();
        for (int i = 1; i < langIds.length; i++) if (langSelected[i] && langIds[i] == id) return true;
        return false;
    }

    private boolean matchesCategory(Channel c) {
        if (catSelected[0]) return true;
        int id = c.getChannelCategoryId();
        for (int i = 1; i < catIds.length; i++) if (catSelected[i] && catIds[i] == id) return true;
        return false;
    }

    private void showChannelLoadFailure(String msg, boolean toast) {
        if (toast) Toast.makeText(getApplicationContext(), "JSON Error", Toast.LENGTH_LONG).show();
        showBlackScreen(msg);
    }

    /** Keep the TV display awake only while the foreground player is active. */
    private void setPlaybackKeepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) playerView.setKeepScreenOn(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (playerView != null) playerView.setKeepScreenOn(false);
        }
    }

    private void showBlackScreen(String msg) {
        setPlaybackKeepScreenOn(false);
        playerManager.release();
        login.setVisibility(View.GONE); gridContainer.setVisibility(View.GONE); overlay.setVisibility(View.GONE);
        epgOverlayRoot.setVisibility(View.GONE);
        handler.removeCallbacks(hidePlayerProgramBanner);
        playerProgramBanner.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE); playerView.setBackgroundColor(Color.BLACK);
        errorMessage.setText(msg); errorMessage.setVisibility(View.VISIBLE);
    }

    private void setupGrid() {
        grid.setAdapter(new ChannelTileAdapter(this, filteredChannels, session, this::playSelectedChannel, this::onTileFavoriteChanged));
        grid.setOnItemClickListener((p, v, pos, id) -> playSelectedChannel(filteredChannels.get(pos)));
        grid.setOnKeyListener((view, keyCode, event) -> handleGridEnterKey(keyCode, event));
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentIndex = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (!filteredChannels.isEmpty()) grid.setSelection(Math.max(0, Math.min(currentIndex, filteredChannels.size() - 1)));
        prefetchPlaybackUrls();
    }

    private void onTileFavoriteChanged(Channel channel) {
        if (channel == null || session == null) return;
        boolean nowFavorite = session.toggleFavoriteChannel(channel.getChannelId());
        applyFilters();
        Toast.makeText(getApplicationContext(),
                nowFavorite ? "Added to favorites" : "Removed from favorites",
                Toast.LENGTH_SHORT).show();
    }

    private void prefetchPlaybackUrls() {
        List<Channel> toPrefetch = new ArrayList<>(filteredChannels);
        prefetchExecutor.execute(() -> {
            for (Channel channel : toPrefetch) {
                if (!session.isLoggedIn() || channel.isCacheValid()) continue;
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            }
        });
    }

    private void playSelectedChannel(Channel channel) {
        currentIndex = findFilteredIndexById(channel.getChannelId());
        if (!channel.isSubscribed()) { Toast.makeText(getApplicationContext(), "Not Subscribed", Toast.LENGTH_LONG).show(); /*return;*/ }
        currentProgram = null;
        handler.removeCallbacks(hidePlayerProgramBanner);
        playerProgramBanner.setVisibility(View.GONE);
        playerProgramBannerVisible = false;
        showPlayerMode();
        viewModel.playChannel(channel);
        loadCurrentProgram(channel);
    }

    private int findFilteredIndexById(int id) {
        for (int i = 0; i < filteredChannels.size(); i++) if (filteredChannels.get(i).getChannelId() == id) return i;
        return -1;
    }

    private void showPlaybackFailure(String msg) {
        setPlaybackKeepScreenOn(false);
        playerManager.release();
        try {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Logger.e("MYJIO", "Failed to show toast", e);
        }
        showGridMode(false);
    }


    private void setupEpgUi() {
        if (btnEpg != null) {
            setupIconWithTooltip(btnEpg, "EPG");
            btnEpg.setOnClickListener(v -> showEpgOverlay(false, false));
        }
        if (playerEpgButton != null) {
            setupIconWithTooltip(playerEpgButton, "EPG");
            playerEpgButton.setOnClickListener(v -> {
                handler.removeCallbacks(hidePlayerProgramBanner);
                showEpgOverlay(true, false);
            });
        }
        if (playerFavoriteButton != null) {
            setupIconWithTooltip(playerFavoriteButton, "Favorite");
            playerFavoriteButton.setOnClickListener(v -> toggleCurrentFavorite());
        }
        if (btnLogout != null) {
            setupIconWithTooltip(btnLogout, "Logout");
        }
        if (epgOverlayRoot != null) {
            epgOverlayRoot.setFocusable(false);
            epgOverlayRoot.setFocusableInTouchMode(false);
            epgOverlayRoot.setOnClickListener(v -> { /* consume clicks outside the panel */ });
            View close = findViewById(R.id.epg_close);
            if (close != null) {
                close.setFocusable(true);
                close.setFocusableInTouchMode(true);
                close.setClickable(true);
                close.setBackgroundResource(R.drawable.epg_close_focus_selector);
                close.setForeground(getDrawable(R.drawable.epg_focus_overlay));
                close.setOnClickListener(v -> hideEpgOverlay());
                setupIconWithTooltip(close, "Close EPG");
            }
        }
    }

    /**
     * Gives Android-TV icon controls a consistent light transparent Jio red
     * selection rectangle and an anchored tooltip when focused/hovered.
     */
    private void setupIconWithTooltip(final View icon, final String text) {
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setForeground(getDrawable(R.drawable.epg_focus_overlay));
        TooltipCompat.setTooltipText(icon, text);

        icon.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showIconTooltip(v, text);
            else hideIconTooltip();
        });
        icon.setOnHoverListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) {
                showIconTooltip(v, text);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                hideIconTooltip();
            }
            return false;
        });
    }

    private void showIconTooltip(final View anchor, final String text) {
        hideIconTooltip();
        pendingTooltip = () -> {
            if (!anchor.isShown() || !anchor.hasFocus()) return;
            TextView tip = new TextView(this);
            tip.setText(text);
            tip.setTextColor(Color.WHITE);
            tip.setTextSize(14);
            tip.setGravity(Gravity.CENTER);
            tip.setPadding(dp(12), dp(7), dp(12), dp(7));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xEE202020);
            bg.setStroke(dp(1), 0x80FF0000);
            bg.setCornerRadius(dp(4));
            tip.setBackground(bg);

            iconTooltipPopup = new PopupWindow(tip, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            iconTooltipPopup.setOutsideTouchable(false);
            iconTooltipPopup.setFocusable(false);
            iconTooltipPopup.setElevation(dp(8));

            // Position the tooltip ABOVE the focused icon.
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            tip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int x = location[0] + (anchor.getWidth() - tip.getMeasuredWidth()) / 2;
            int y = location[1] - tip.getMeasuredHeight() - dp(8);
            iconTooltipPopup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, Math.max(dp(4), y));
        };
        tooltipHandler.postDelayed(pendingTooltip, 120);
    }

    private void hideIconTooltip() {
        if (pendingTooltip != null) {
            tooltipHandler.removeCallbacks(pendingTooltip);
            pendingTooltip = null;
        }
        if (iconTooltipPopup != null) {
            iconTooltipPopup.dismiss();
            iconTooltipPopup = null;
        }
    }

    /**
     * Shows the EPG without releasing the player. This is deliberately an overlay:
     * the current channel continues playing underneath it.
     */
    private void showEpgOverlay(boolean openedFromPlayer, boolean backFromPlayer) {
        if (isFinishing() || isDestroyed()) return;
        if (filteredChannels.isEmpty()) {
            Toast.makeText(this, "No channels available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (epgRepository == null) {
            epgRepository = new EpgRepository(getApplicationContext());
        }

        epgOpenedFromPlayer = openedFromPlayer;
        epgBackFromPlayerGoesToGrid = backFromPlayer;
        epgGeneration++;
        final long generation = epgGeneration;

        if (!openedFromPlayer) {
            playerProgramBanner.setVisibility(View.GONE);
            playerProgramBannerVisible = false;
        }
        overlay.setVisibility(View.GONE);
        epgOverlayRoot.setVisibility(View.VISIBLE);

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
        epgDate.setText(dateFmt.format(new Date()));

        epgRowsContainer.removeAllViews();
        epgRowViews.clear();
        epgLoadMoreView = null;
        epgHorizontalScrolls.clear();
        epgTimeHeaderScroll.scrollTo(0, 0);
        buildEpgTimeHeader();
        epgStatus.setText("Loading EPG…");

        // Never instantiate/request EPG for hundreds of channels at once. The
        // channel list can contain many hundreds of entries; doing so creates a
        // huge view tree and hundreds of concurrent HTTP requests on Android TV.
        epgNextChannelIndex = 0;
        appendEpgPage(generation);
    }

    private void appendEpgPage(final long generation) {
        if (generation != epgGeneration || epgOverlayRoot.getVisibility() != View.VISIBLE) return;

        if (epgLoadMoreView != null) {
            epgRowsContainer.removeView(epgLoadMoreView);
            epgLoadMoreView = null;
        }

        final int start = epgNextChannelIndex;
        final int end = Math.min(start + EPG_PAGE_SIZE, filteredChannels.size());
        epgNextChannelIndex = end;

        for (int i = start; i < end; i++) {
            Channel channel = filteredChannels.get(i);
            View row = createEpgRow(channel);
            epgRowsContainer.addView(row);
            epgRowViews.put(channel.getChannelId(), row);

            final int channelId = channel.getChannelId();
            epgRepository.getPrograms(channelId, 0, new EpgRepository.Callback() {
                @Override
                public void onSuccess(List<EpgProgram> programs) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || generation != epgGeneration ||
                                epgOverlayRoot.getVisibility() != View.VISIBLE) return;
                        updateEpgRow(channel, programs);
                    });
                }

                @Override
                public void onFailure(String message) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || generation != epgGeneration ||
                                epgOverlayRoot.getVisibility() != View.VISIBLE) return;
                        View rowView = epgRowViews.get(channelId);
                        if (rowView != null) {
                            TextView status = rowView.findViewWithTag("epg_row_status");
                            if (status != null) status.setText("EPG unavailable");
                        }
                        Logger.w("EPG", "CH " + channelId + ": " + message);
                    });
                }
            });
        }

        if (epgNextChannelIndex < filteredChannels.size()) {
            epgLoadMoreView = textView(
                    "Load more channels  (" + epgNextChannelIndex + "/" + filteredChannels.size() + ")",
                    16, Color.WHITE);
            epgLoadMoreView.setGravity(Gravity.CENTER);
            epgLoadMoreView.setFocusable(true);
            epgLoadMoreView.setClickable(true);
            epgLoadMoreView.setBackgroundResource(R.drawable.epg_load_more_selector);
            epgLoadMoreView.setForeground(getDrawable(R.drawable.epg_focus_overlay));
            epgLoadMoreView.setPadding(dp(12), dp(12), dp(12), dp(12));
            epgLoadMoreView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.setBackgroundResource(R.drawable.epg_load_more_selector);
                }
            });
            epgLoadMoreView.setOnClickListener(v -> {
                // Keep the focus target at the end of the newly appended page.
                // appendEpgPage() removes the old Load More view, appends the next
                // 100 rows, and creates a new Load More view after those rows.
                epgLoadMoreView = null;
                appendEpgPage(generation);
                if (epgLoadMoreView != null) {
                    final View nextLoadMore = epgLoadMoreView;
                    nextLoadMore.post(() -> {
                        if (generation == epgGeneration &&
                                epgOverlayRoot.getVisibility() == View.VISIBLE) {
                            nextLoadMore.requestFocus();
                            ScrollView scroll = findViewById(R.id.epg_scroll);
                            if (scroll != null) {
                                scroll.post(() -> scroll.smoothScrollTo(0,
                                        Math.max(0, nextLoadMore.getBottom() - scroll.getHeight())));
                            }
                        }
                    });
                }
            });
            epgRowsContainer.addView(epgLoadMoreView,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            epgStatus.setText("Showing " + epgNextChannelIndex + " of " + filteredChannels.size() +
                    " channels • Select Load more for additional channels");
        } else {
            epgStatus.setText("EPG loaded for " + epgNextChannelIndex + " channels");
        }

        // Give focus to the first actual channel/program area only on the first page.
        if (start == 0 && epgRowsContainer.getChildCount() > 0) {
            View firstRow = epgRowsContainer.getChildAt(0);
            if (firstRow instanceof ViewGroup && ((ViewGroup) firstRow).getChildCount() > 0) {
                View firstInfo = ((ViewGroup) firstRow).getChildAt(0);
                firstInfo.setFocusable(true);
                firstInfo.requestFocus();
            } else {
                View close = findViewById(R.id.epg_close);
                if (close != null) close.requestFocus();
            }
        }
    }

    private void showEpgOverlay() {
        showEpgOverlay(playerView != null && playerView.getVisibility() == View.VISIBLE, false);
    }

    /**
     * Handles a user Back while the EPG overlay is visible.
     *
     * Player -> Back opens EPG with epgBackFromPlayerGoesToGrid=true.
     * The next Back closes EPG and returns to Channel Tiles.
     * EPG opened by the player-banner icon returns to the player instead.
     */
    private void hideEpgOverlay() {
        epgGeneration++;
        removeEpgLoadMoreView();
        epgOverlayRoot.setVisibility(View.GONE);

        if (epgBackFromPlayerGoesToGrid) {
            epgBackFromPlayerGoesToGrid = false;
            epgOpenedFromPlayer = false;
            currentProgram = null;
            playerProgramBanner.setVisibility(View.GONE);
            playerProgramBannerVisible = false;
            showGridMode(true);
        } else if (epgOpenedFromPlayer && playerView.getVisibility() == View.VISIBLE) {
            epgOpenedFromPlayer = false;
            playerProgramBanner.setVisibility(playerProgramBannerVisible ? View.VISIBLE : View.GONE);
            if (playerProgramBannerVisible) {
                Channel ch = viewModel.getCurrentChannel().getValue();
                if (ch != null && currentProgram != null) updatePlayerProgramBanner(ch, currentProgram);
                playerProgramBanner.setVisibility(View.VISIBLE);
                schedulePlayerProgramBannerHide();
            }
            playerEpgButton.requestFocus();
        } else {
            epgOpenedFromPlayer = false;
            grid.requestFocus();
        }
    }

    /** Closes EPG because the user selected a channel/program. */
    private void closeEpgForPlayback() {
        epgGeneration++;
        removeEpgLoadMoreView();
        epgOverlayRoot.setVisibility(View.GONE);
        epgOpenedFromPlayer = false;
        epgBackFromPlayerGoesToGrid = false;
    }

    private void removeEpgLoadMoreView() {
        if (epgLoadMoreView != null) {
            epgRowsContainer.removeView(epgLoadMoreView);
            epgLoadMoreView = null;
        }
    }

    private View createEpgRow(Channel channel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(24, 24, 24));
        row.setFocusable(false);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(dp(310), dp(92));
        row.addView(info, infoLp);

        TextView number = textView(String.valueOf(channel.getChannelNumber()), 15, Color.LTGRAY);
        number.setGravity(Gravity.CENTER);
        info.addView(number, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ChannelLogoLoader.load(this, logo, channel.getLogoUrl());
        info.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = textView(channel.getChannelName(), 14, Color.WHITE);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView category = textView(channel.getChannelCategory(), 11, Color.GRAY);
        names.addView(name, new LinearLayout.LayoutParams(dp(180), dp(44)));
        names.addView(category, new LinearLayout.LayoutParams(dp(180), dp(25)));
        info.addView(names);

        info.setFocusable(true);
        info.setFocusableInTouchMode(true);
        info.setClickable(true);
        info.setForeground(getDrawable(R.drawable.epg_focus_overlay));
        info.setOnFocusChangeListener((v, hasFocus) -> v.setSelected(hasFocus));
        info.setOnClickListener(v -> {
            closeEpgForPlayback();
            currentIndex = findFilteredIndexById(channel.getChannelId());
            playSelectedChannel(channel);
        });

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(false);
        hsv.setTag("epg_hsv");

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.HORIZONTAL);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        timeline.setMinimumWidth(dp(2880));
        hsv.addView(timeline, new HorizontalScrollView.LayoutParams(
                dp(2880), dp(92)));

        epgHorizontalScrolls.add(hsv);
        hsv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (epgTimeHeaderScroll != null && epgTimeHeaderScroll.getScrollX() != scrollX) {
                epgTimeHeaderScroll.scrollTo(scrollX, 0);
            }
            for (HorizontalScrollView other : epgHorizontalScrolls) {
                if (other != v && other.getScrollX() != scrollX) {
                    other.scrollTo(scrollX, 0);
                }
            }
        });

        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(0, dp(92), 1f);
        row.addView(hsv, hsvLp);

        TextView status = textView("Loading…", 12, Color.GRAY);
        status.setGravity(Gravity.CENTER);
        status.setTag("epg_row_status");
        timeline.addView(status, new LinearLayout.LayoutParams(dp(120), dp(60)));

        row.setTag(channel.getChannelId());
        return row;
    }

    private void updateEpgRow(Channel channel, List<EpgProgram> programs) {
        View row = epgRowViews.get(channel.getChannelId());
        if (row == null) return;
        HorizontalScrollView hsv = row.findViewWithTag("epg_hsv");
        if (hsv == null || hsv.getChildCount() == 0) return;
        LinearLayout timeline = (LinearLayout) hsv.getChildAt(0);
        timeline.removeAllViews();

        long dayStart = startOfTodaySeconds();
        long dayEnd = dayStart + 24 * 60 * 60L;
        long now = System.currentTimeMillis() / 1000L;
        int added = 0;

        for (EpgProgram p : programs) {
            long start = Math.max(dayStart, p.getStartEpoch());
            long end = Math.min(dayEnd, p.getEndEpoch());
            if (end <= dayStart || start >= dayEnd || end <= start) continue;

            int minutes = Math.max(1, (int) ((end - start) / 60L));
            int widthDp = Math.max(112, minutes * 2);

            TextView block = textView(
                    formatTime(p.getStartEpoch()) + "\n" +
                    (p.getTitle().isEmpty() ? "Program" : p.getTitle()),
                    13, Color.WHITE);
            block.setGravity(Gravity.CENTER_VERTICAL);
            block.setPadding(dp(10), 0, dp(10), 0);
            block.setMaxLines(2);
            block.setEllipsize(android.text.TextUtils.TruncateAt.END);
            block.setFocusable(true);
            block.setFocusableInTouchMode(true);
            block.setClickable(true);
            block.setForeground(getDrawable(R.drawable.epg_focus_overlay));
            block.setOnFocusChangeListener((v, hasFocus) -> v.setSelected(hasFocus));

            int fill;
            if (p.isCurrent(now)) fill = Color.rgb(150, 0, 35);
            else if (p.isPast(now)) fill = Color.rgb(58, 58, 58);
            else fill = Color.rgb(42, 42, 42);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(fill);
            bg.setStroke(dp(1), Color.rgb(75, 75, 75));
            bg.setCornerRadius(dp(4));
            block.setBackground(bg);

            block.setOnClickListener(v -> onEpgProgramSelected(channel, p));
            timeline.addView(block, new LinearLayout.LayoutParams(dp(widthDp), dp(72)));
            added++;
        }

        if (added == 0) {
            TextView empty = textView("No programs", 12, Color.GRAY);
            empty.setGravity(Gravity.CENTER);
            timeline.addView(empty, new LinearLayout.LayoutParams(dp(160), dp(60)));
        }
    }

    private void buildEpgTimeHeader() {
        epgTimeHeader.removeAllViews();
        for (int hour = 0; hour < 24; hour++) {
            TextView t = textView(String.format(Locale.US, "%02d:00", hour), 13, Color.LTGRAY);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(8), 0, 0, 0);
            epgTimeHeader.addView(t, new LinearLayout.LayoutParams(dp(120), dp(42)));
        }
    }

    private void onEpgProgramSelected(Channel channel, EpgProgram program) {
        long now = System.currentTimeMillis() / 1000L;
        if (program.isFuture(now)) {
            Toast.makeText(this, "Program has not started yet", Toast.LENGTH_SHORT).show();
            return;
        }

        closeEpgForPlayback();
        currentIndex = findFilteredIndexById(channel.getChannelId());
        showPlayerMode();

        // If a past item has no catch-up entitlement, retain the original
        // application's safe behavior of playing the channel live instead.
        if (program.isPast(now) && !channel.isCatchupAvailable()) {
            viewModel.playChannel(channel);
        } else {
            viewModel.playProgram(channel, program);
        }
        loadCurrentProgram(channel);
    }

    private void loadCurrentProgram(Channel channel) {
        if (epgRepository == null) return;
        final int requestedId = channel.getChannelId();
        epgRepository.getCurrentProgram(requestedId, new EpgRepository.Callback() {
            @Override
            public void onSuccess(List<EpgProgram> programs) {
                runOnUiThread(() -> {
                    if (viewModel.getCurrentChannel().getValue() == null ||
                            viewModel.getCurrentChannel().getValue().getChannelId() != requestedId) {
                        return;
                    }
                    if (programs.isEmpty()) {
                        currentProgram = null;
                        playerProgramBanner.setVisibility(View.GONE);
                        return;
                    }
                    currentProgram = programs.get(0);
                    // Program metadata is updated silently. The banner is shown only
                    // when the user presses BACK while the channel is playing.
                    if (playerProgramBannerVisible) {
                        updatePlayerProgramBanner(channel, currentProgram);
                    }
                });
            }

            @Override
            public void onFailure(String message) {
                Logger.w("EPG", "Current program unavailable: " + message);
            }
        });
    }

    private void updatePlayerProgramBanner(Channel channel, EpgProgram program) {
        playerProgramChannel.setText("CH " + channel.getChannelNumber() + "  " + channel.getChannelName());
        playerProgramTitle.setText(program.getTitle().isEmpty() ? "Live TV" : program.getTitle());
        String category = program.getShowCategory().isEmpty()
                ? channel.getChannelCategory() : program.getShowCategory();
        String time = formatTime(program.getStartEpoch()) + " - " + formatTime(program.getEndEpoch());
        playerProgramCategory.setText(category + "  •  " + time);
        ChannelLogoLoader.load(this, playerProgramLogo, channel.getLogoUrl());
        updateFavoriteIcon(channel);
    }

    private void showPlayerProgramBanner() {
        Channel ch = viewModel.getCurrentChannel().getValue();
        if (ch == null) return;
        if (currentProgram == null) {
            loadCurrentProgram(ch);
            // The async callback will update the banner if it is already visible.
        } else {
            updatePlayerProgramBanner(ch, currentProgram);
        }
        handler.removeCallbacks(hidePlayerProgramBanner);
        playerProgramBanner.setVisibility(View.VISIBLE);
        playerProgramBannerVisible = true;
        schedulePlayerProgramBannerHide();
        playerEpgButton.requestFocus();
    }

    private void schedulePlayerProgramBannerHide() {
        handler.removeCallbacks(hidePlayerProgramBanner);
        handler.postDelayed(hidePlayerProgramBanner, PLAYER_BANNER_AUTO_HIDE_MS);
    }

    private void updateFavoriteIcon(Channel channel) {
        boolean favorite = session.isFavoriteChannel(channel.getChannelId());
        playerFavoriteButton.setImageResource(
                favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_outline);
        playerFavoriteButton.setContentDescription(favorite ? "Remove favorite" : "Add favorite");
        TooltipCompat.setTooltipText(playerFavoriteButton,
                favorite ? "Remove favorite" : "Favorite");
    }

    private void toggleCurrentFavorite() {
        Channel channel = viewModel.getCurrentChannel().getValue();
        if (channel == null) return;
        boolean nowFavorite = session.toggleFavoriteChannel(channel.getChannelId());
        updateFavoriteIcon(channel);
        applyFilters();
        Toast.makeText(this, nowFavorite ? "Added to favorites" : "Removed from favorites",
                Toast.LENGTH_SHORT).show();
    }

    private static String formatTime(long epochSeconds) {
        if (epochSeconds > 100000000000L) epochSeconds /= 1000L;
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(epochSeconds * 1000L));
    }

    private static long startOfTodaySeconds() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 1000L;
    }

    private TextView textView(String text, int sizeSp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showChannelOverlay(Channel ch) {
        channelNumber.setText("CH " + ch.getChannelNumber()); channelName.setText(ch.getChannelName());
        overlay.setVisibility(View.VISIBLE); handler.removeCallbacks(hideOverlay); handler.postDelayed(hideOverlay, 4000);
    }

    private void showPlayerMode() { 
        login.setVisibility(View.GONE); 
        gridContainer.setVisibility(View.GONE); 
        errorMessage.setVisibility(View.GONE); 
        playerView.setVisibility(View.VISIBLE);
        playerView.setBackgroundColor(Color.TRANSPARENT);
    }
    
    private void showLoginMode() {
        setPlaybackKeepScreenOn(false);
        playerManager.release();
        login.setVisibility(View.VISIBLE);
        gridContainer.setVisibility(View.GONE);
        playerView.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        epgOverlayRoot.setVisibility(View.GONE);
        playerProgramBanner.setVisibility(View.GONE);
        playerProgramBannerVisible = false;
        errorMessage.setVisibility(View.GONE);
    }

    private void showGridMode() { showGridMode(true); }

    private void showGridMode(boolean stop) {
        if (stop) playerManager.release();
        epgGeneration++;
        epgOpenedFromPlayer = false;
        epgBackFromPlayerGoesToGrid = false;
        currentProgram = null;
        gridContainer.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);
        login.setVisibility(View.GONE);
        errorMessage.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        epgOverlayRoot.setVisibility(View.GONE);
        playerProgramBanner.setVisibility(View.GONE);
        playerProgramBannerVisible = false;
        if (!filteredChannels.isEmpty()) {
            grid.setSelection(Math.max(0, Math.min(currentIndex, filteredChannels.size() - 1)));
        }
        grid.requestFocus();
    }

    private void navigateChannel(int step) {
        if (filteredChannels.isEmpty()) return;
        currentIndex = (currentIndex + step + filteredChannels.size()) % filteredChannels.size();
        playSelectedChannel(filteredChannels.get(currentIndex));
    }

    private void appendDigit(int d) { if (digits.length() >= 5) digits.setLength(0); digits.append(d); channelNumber.setText("CH " + digits); overlay.setVisibility(View.VISIBLE); handler.removeCallbacks(commitDigits); handler.postDelayed(commitDigits, 1600); }
    private void commitDigits() {
        if (digits.length() == 0) return;
        int num; try { num = Integer.parseInt(digits.toString()); } catch (Exception e) { digits.setLength(0); return; }
        digits.setLength(0);
        int idx = -1; for (int i = 0; i < filteredChannels.size(); i++) if (filteredChannels.get(i).getChannelNumber() == num) { idx = i; break; }
        if (idx >= 0) { currentIndex = idx; playSelectedChannel(filteredChannels.get(idx)); }
        else {
            boolean ex = false; for (Channel c : allChannels) if (c.getChannelNumber() == num) { ex = true; break; }
            Toast.makeText(getApplicationContext(), ex ? "Channel Filtered Out By User" : "Not Found", Toast.LENGTH_SHORT).show();
            handler.postDelayed(hideOverlay, 1000);
        }
    }


    /**
     * Keeps Android-TV DPAD focus trapped inside the EPG overlay. If Android's
     * normal focus search would leave the overlay, the current EPG control keeps
     * focus instead.
     */
    private boolean handleEpgDpadNavigation(int keyCode) {
        if (epgOverlayRoot == null || epgOverlayRoot.getVisibility() != View.VISIBLE) return true;

        View current = getCurrentFocus();
        if (current == null || !isDescendantOf(current, epgOverlayRoot)) {
            View close = findViewById(R.id.epg_close);
            if (close != null) close.requestFocus();
            return true;
        }

        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            default: return true;
        }

        View next = current.focusSearch(direction);
        if (next != null && isDescendantOf(next, epgOverlayRoot)) {
            next.requestFocus();
        }
        // No valid focus target inside EPG: deliberately consume the key and
        // retain focus instead of allowing focus to escape to the background UI.
        return true;
    }

    private boolean isDescendantOf(View child, ViewGroup parent) {
        View v = child;
        while (v != null) {
            if (v == parent) return true;
            if (v.getParent() instanceof View) v = (View) v.getParent();
            else break;
        }
        return false;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (session == null || !session.isLoggedIn()) return super.onKeyDown(keyCode, event);

        // EPG owns DPAD/Back while it is visible. Do not allow the underlying
        // PlayerView or Channel Tiles UI to receive navigation events.
        if (epgOverlayRoot != null && epgOverlayRoot.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_GUIDE) {
                hideEpgOverlay();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return handleEpgDpadNavigation(keyCode);
            }
            return true;
        }

        // A single OK/ENTER event while playing shows the program banner. There
        // is intentionally no dispatchKeyEvent override anymore; handling it here
        // avoids duplicate processing of the same remote-control event.
        if (playerView.getVisibility() == View.VISIBLE &&
                isEnterKey(keyCode) && event.getRepeatCount() == 0) {
            showPlayerProgramBanner();
            return true;
        }

        if (handleGridEnterKey(keyCode, event)) return true;

        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (gridContainer.getVisibility() == View.VISIBLE) {
                toggleVoiceSearch();
                return true;
            }
        }

        if (event.getRepeatCount() == 0) {
            if (isNextChannelKey(keyCode)) {
                if (playerView.getVisibility() == View.VISIBLE) navigateChannel(1);
                else return super.onKeyDown(keyCode, event);
                return true;
            }
            if (isPreviousChannelKey(keyCode)) {
                if (playerView.getVisibility() == View.VISIBLE) navigateChannel(-1);
                else return super.onKeyDown(keyCode, event);
                return true;
            }
        }

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendDigit(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_GUIDE) {
            // EPG is intentionally opened only through the on-screen EPG icon.
            return true;
        }

        // While playing, the first BACK reveals the current-program banner.
        // A second BACK while that banner is visible leaves playback and returns
        // to Channel Tiles. The EPG overlay is never opened by BACK.
        if (keyCode == KeyEvent.KEYCODE_BACK && playerView.getVisibility() == View.VISIBLE) {
            if (playerProgramBannerVisible) {
                handler.removeCallbacks(hidePlayerProgramBanner);
                showGridMode(true);
            } else {
                showPlayerProgramBanner();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_ASSIST) {
            if (gridContainer.getVisibility() == View.VISIBLE) return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean handleGridEnterKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0 || gridContainer.getVisibility() != View.VISIBLE || !isEnterKey(keyCode)) return false;
        int pos = grid.getSelectedItemPosition();
        if (pos >= 0 && pos < filteredChannels.size()) { playSelectedChannel(filteredChannels.get(pos)); return true; }
        return false;
    }

    private static boolean isEnterKey(int k) { return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER; }
    private static boolean isNextChannelKey(int k) { return k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_BUTTON_R1; }
    private static boolean isPreviousChannelKey(int k) { return k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_BUTTON_L1; }

    @Override protected void onResume() { super.onResume(); handler.post(updateClock); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(updateClock); handler.removeCallbacks(hidePlayerProgramBanner); }
    @Override protected void onStop() { super.onStop(); playerManager.release(); if (speechRecognizer != null) speechRecognizer.cancel(); }
    @Override protected void onDestroy() { 
        hideIconTooltip();
        tooltipHandler.removeCallbacksAndMessages(null);
        setPlaybackKeepScreenOn(false);
        playerManager.release(); 
        channelExecutor.shutdownNow(); 
        prefetchExecutor.shutdownNow(); 
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (epgRepository != null) { epgRepository.shutdown(); epgRepository = null; }
        super.onDestroy(); 
    }

}
