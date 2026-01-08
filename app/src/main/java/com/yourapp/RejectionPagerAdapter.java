package com.yourapp;


import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import org.jspecify.annotations.NonNull;

public class RejectionPagerAdapter extends FragmentStateAdapter {

    private String farm = "", from = "", to = "";

    public RejectionPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return RejectionTabFragment.newInstance(position, farm, from, to);
    }

    @Override
    public int getItemCount() {
        return 7;
    }

    public void updateFilters(String farm, String from, String to) {
        this.farm = farm;
        this.from = from;
        this.to = to;
        notifyDataSetChanged();
    }
    @Override
    public long getItemId(int position) {
        return (position + farm + from + to).hashCode();
    }

    @Override
    public boolean containsItem(long itemId) {
        return true;
    }

}
