package com.outlay.view.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;

import com.outlay.App;
import com.outlay.R;
import com.outlay.adapter.CategoriesGridAdapter;
import com.outlay.dao.Category;
import com.outlay.dao.Expense;
import com.outlay.helper.TextWatcherAdapter;
import com.outlay.model.Summary;
import com.outlay.presenter.MainFragmentPresenter;
import com.outlay.utils.DateUtils;
import com.outlay.utils.DeviceUtils;
import com.outlay.utils.ResourceUtils;
import com.outlay.view.Page;
import com.outlay.view.activity.BaseActivity;
import com.outlay.view.alert.Alert;
import com.outlay.view.dialog.DatePickerFragment;
import com.outlay.view.numpad.NumpadEditable;
import com.outlay.view.numpad.NumpadView;
import com.outlay.view.numpad.SimpleNumpadValidator;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainFragment extends BaseFragment implements AppBarLayout.OnOffsetChangedListener {
    private static final float PERCENTAGE_TO_HIDE_TITLE_DETAILS = 0.2f;
    private static final int ALPHA_ANIMATIONS_DURATION = 200;

    @Nullable
    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.amountEditable)
    EditText amountText;

    @Bind(R.id.numpadView)
    NumpadView numpadView;

    @Bind(R.id.categoriesGrid)
    RecyclerView categoriesGrid;

    @Bind(R.id.appbar)
    AppBarLayout appbar;

    @Bind(R.id.toolbarAmountValue)
    TextView toolbarAmountValue;

    @Bind(R.id.toolbarContainer)
    View toolbarContainer;

    @Bind(R.id.dateLabel)
    TextView dateLabel;

    @Bind(R.id.coordinatorLayout)
    CoordinatorLayout coordinatorLayout;

    @Inject
    MainFragmentPresenter presenter;

    private CategoriesGridAdapter adapter;
    private boolean mIsTheTitleContainerVisible = false;
    private Date selectedDate = new Date();
    private SimpleNumpadValidator validator = new SimpleNumpadValidator() {
        @Override
        public void onInvalidInput(String value) {
            super.onInvalidInput(value);
            inputError();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.getComponent(getActivity()).inject(this);
        presenter.attachView(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, null, false);
        ButterKnife.bind(this, view);
        enableToolbar(toolbar);
        ((BaseActivity) getActivity()).setupDrawer(toolbar);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appbar.addOnOffsetChangedListener(this);
        startAlphaAnimation(toolbarContainer, 0, View.INVISIBLE);

        numpadView.attachEditable(new NumpadEditable() {
            @Override
            public String getText() {
                return amountText.getText().toString();
            }

            @Override
            public void setText(String text) {
                amountText.setText(text);
            }
        }, validator);
        amountText.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                toolbarAmountValue.setText(s);
            }
        });

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 4);
        categoriesGrid.setLayoutManager(gridLayoutManager);

        int availableHeight = DeviceUtils.getScreenSize(getActivity()).heightPixels
                - DeviceUtils.getStatusBarHeight(getActivity())
                - DeviceUtils.getActionBarHeight(getActivity()); // Bottom panel has actionBarHeight
        int categoriesGridHeight = (int) (availableHeight / 2.7f);
        appbar.getLayoutParams().height = availableHeight - categoriesGridHeight;

        adapter = new CategoriesGridAdapter(new CategoriesGridAdapter.Style(categoriesGridHeight / 2));
        adapter.setOnCategoryClickListener(c -> {
            if (validator.valid(amountText.getText().toString())) {
                Expense e = new Expense();
                e.setCategory(c);
                e.setAmount(Double.valueOf(amountText.getText().toString()));
                e.setReportedAt(selectedDate);
                presenter.insertExpense(e);
                presenter.loadSummary(new Date());
                amountText.setText("");

                String message = getString(R.string.info_expense_created);
                message = String.format(message, e.getAmount(), e.getCategory().getTitle());
                Alert.info(getRootView(), message,
                    v -> {
                        e.delete();
                        presenter.loadSummary(new Date());
                        amountText.setText(String.valueOf(e.getAmount()));
                    }
                );
            } else {
                validator.onInvalidInput(amountText.getText().toString());
            }
        });
        categoriesGrid.setAdapter(adapter);
        dateLabel.setOnClickListener(v -> {
            DatePickerFragment datePickerFragment = new DatePickerFragment();
            datePickerFragment.setOnDateSetListener((parent, year, monthOfYear, dayOfMonth) -> {
                Calendar c = Calendar.getInstance();
                c.set(year, monthOfYear, dayOfMonth);
                Date selected = c.getTime();
                selectedDate = selected;
                dateLabel.setText(DateUtils.toLongString(selected));
            });
            datePickerFragment.show(getChildFragmentManager(), "datePicker");
        });
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        amountText.setText("");
        presenter.loadCategories();
        presenter.loadSummary(new Date());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
        MenuItem summaryItem = menu.findItem(R.id.action_summary);
        summaryItem.setIcon(ResourceUtils.getCustomToolbarIcon(getActivity(), R.integer.ic_chart));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_summary:
                Page.goToReport(getActivity(), selectedDate);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(verticalOffset) / (float) maxScroll;
        handleAlphaOnTitle(percentage);
    }

    private void handleAlphaOnTitle(float percentage) {
        if (percentage < PERCENTAGE_TO_HIDE_TITLE_DETAILS) {
            if (mIsTheTitleContainerVisible) {
                startAlphaAnimation(toolbarContainer, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                mIsTheTitleContainerVisible = false;
            }
        } else {
            if (!mIsTheTitleContainerVisible) {
                startAlphaAnimation(toolbarContainer, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                mIsTheTitleContainerVisible = true;
            }
        }
    }

    public static void startAlphaAnimation(View v, long duration, int visibility) {
        AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
                ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);

        alphaAnimation.setDuration(duration);
        alphaAnimation.setFillAfter(true);
        v.startAnimation(alphaAnimation);
    }

    public void inputError() {
        Animation shakeAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.shake);
        amountText.startAnimation(shakeAnimation);
    }

    public void displaySummary(Summary summary) {
        ((BaseActivity) getActivity()).updateDrawerData(summary);
    }

    public void displayCategories(List<Category> categoryList) {
        adapter.setItems(categoryList);
    }
}
