package io.Pushjet.api;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import io.Pushjet.api.PushjetApi.PushjetService;

import java.util.ArrayList;
import java.util.Collections;

public class SubscriptionsAdapter extends RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder> {
    private Context context;
    private ArrayList<PushjetService> entries = new ArrayList<PushjetService>();
    private int selected = -1;

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

    public SubscriptionsAdapter(Context context) {
        this.context = context;
    }

    @Override
    public SubscriptionsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RelativeLayout itemView = (RelativeLayout) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.fragment_servicelist, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(SubscriptionsAdapter.ViewHolder holder, final int position) {
        final View itemView = holder.itemView;
        TextView titleText = (TextView) itemView.findViewById(R.id.service_name);
        TextView tokenText = (TextView) itemView.findViewById(R.id.service_token);
        ImageView iconImage = (ImageView) itemView.findViewById(R.id.service_icon_image);

        String title = entries.get(position).getName();
        String token = entries.get(position).getToken();
        Drawable icon = entries.get(position).getIconPlaceholder(context);

        titleText.setText(title);
        tokenText.setText(token);
        if (entries.get(position).hasIcon()) {
            Uri iconUrl = entries.get(position).getIconUri();
            Picasso.with(context)
                    .load(iconUrl)
                    .placeholder(icon)
                    .fit()
                    .centerInside()
                    .into(iconImage);
        } else {
            iconImage.setImageDrawable(icon);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selected = position;
                itemView.showContextMenu();
            }
        });
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getItemCount() {
        return this.entries.size();
    }

    public void addEntries(ArrayList<PushjetService> entries) {
        Collections.reverse(entries);
        for (PushjetService entry : entries)
            this.entries.add(0, entry);
        notifyDataSetChanged();
    }

    public void addEntry(PushjetService entry) {
        this.entries.add(0, entry);
        notifyDataSetChanged();
    }

    public Object getSelectedItem() {
        return entries.get(this.selected);
    }

    public void upDateEntries(ArrayList<PushjetService> entries) {
        Collections.reverse(entries);
        this.entries = entries;
        notifyDataSetChanged();
    }
}
