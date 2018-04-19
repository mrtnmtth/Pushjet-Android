package io.Pushjet.api;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.Pushjet.api.PushjetApi.PushjetMessage;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class PushListAdapter extends RecyclerView.Adapter<PushListAdapter.ViewHolder> {

    private Context context;
    private ArrayList<PushjetMessage> entries = new ArrayList<PushjetMessage>();
    private DateFormat df;
    private int selected = -1;

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

    public PushListAdapter(Context context) {
        this.context = context;
        this.entries = entries;
        this.df = new SimpleDateFormat("d MMM HH:mm"); // 7 jul 15:30
    }

    @Override
    public PushListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RelativeLayout itemView = (RelativeLayout) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.fragment_pushlist, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        View itemView = holder.itemView;
        TextView dateText = (TextView) itemView.findViewById(R.id.push_date);
        TextView titleText = (TextView) itemView.findViewById(R.id.push_title);
        TextView descriptionText = (TextView) itemView.findViewById(R.id.push_description);
        ImageView iconImage = (ImageView) itemView.findViewById(R.id.push_icon_image);
        ImageButton linkButton = (ImageButton) itemView.findViewById(R.id.push_link_button);

        String title = entries.get(position).getTitle();
        if (title.equals(""))
            title = entries.get(position).getService().getName();
        final String description = entries.get(position).getMessage();
        Date pushDate = entries.get(position).getTimestamp();
        Drawable icon = entries.get(position).getService().getIconBitmapOrDefault(context);

        dateText.setText(this.df.format(pushDate));
        titleText.setText(title);
        descriptionText.setText(description);
        iconImage.setImageDrawable(icon);

        if (entries.get(position).hasLink()) {
            final String link = entries.get(position).getLink();
            linkButton.setVisibility(View.VISIBLE);
            linkButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent linkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    if (linkIntent.resolveActivity(context.getPackageManager()) != null)
                        context.startActivity(linkIntent);
                    else
                        Toast.makeText(context, "Could not find app to handle link\n" + link,
                                Toast.LENGTH_LONG).show();
                }
            });
        }
        else
            linkButton.setVisibility(View.GONE);

        // expand on click
        final int pos = holder.getAdapterPosition();
        final boolean isExpanded = pos == selected;
        descriptionText.setSingleLine(!isExpanded);
        holder.itemView.setActivated(isExpanded);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected = isExpanded ? -1 : pos;
                notifyItemChanged(pos);
            }
        });

        // copy message on long click
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                MiscUtil.WriteToClipboard(description, "Pushjet message", context);
                Toast.makeText(context, "Copied message to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return this.entries.size();
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void clearSelected() {
        setSelected(-1);
    }

    public void addEntries(ArrayList<PushjetMessage> entries) {
        for (PushjetMessage entry : entries)
            this.entries.add(0, entry);
        notifyDataSetChanged();
    }

    public void addEntry(PushjetMessage entry) {
        this.entries.add(0, entry);
        notifyDataSetChanged();
    }

    public void upDateEntries(ArrayList<PushjetMessage> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }
}