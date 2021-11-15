package com.CadeMixedUpGame.phoneapp;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class SpinnerAdapter extends ArrayAdapter<DiffGoogleVoice> {

    private Context context;
    private ArrayList<DiffGoogleVoice> statuses;
    public Resources res;
    DiffGoogleVoice currRowVal = null;
    LayoutInflater inflater;

    public SpinnerAdapter(Context context, int textViewResourceId, ArrayList<DiffGoogleVoice> statuses,
                         Resources resLocal) {
        super(context, textViewResourceId, statuses);
        this.context = context;
        this.statuses = statuses;
        this.res = resLocal;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    public View getCustomView(int position, View convertView, ViewGroup parent) {
        View row = inflater.inflate(R.layout.read_method_item, parent, false);
        currRowVal = null;
        currRowVal = (DiffGoogleVoice) statuses.get(position);
        TextView label = (TextView) row.findViewById(R.id.spinnerItem);
        if (position == 0) {
            label.setText("Regular google voice");
        } else {
            label.setText(currRowVal.getVoice());
        }

        return row;
    }

}



