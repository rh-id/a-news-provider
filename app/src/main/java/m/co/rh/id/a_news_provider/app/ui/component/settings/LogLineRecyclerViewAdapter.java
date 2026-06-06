package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.R;

public class LogLineRecyclerViewAdapter extends RecyclerView.Adapter<LogLineRecyclerViewAdapter.LogLineViewHolder> {
    private List<String> mLogLines = new ArrayList<>();

    public LogLineRecyclerViewAdapter() {
    }

    @NonNull
    @Override
    public LogLineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new LogLineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogLineViewHolder holder, int position) {
        holder.mTextView.setText(mLogLines.get(position));
    }

    @Override
    public int getItemCount() {
        return mLogLines.size();
    }

    public void setItems(List<String> logLines) {
        mLogLines = logLines != null ? logLines : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class LogLineViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;

        public LogLineViewHolder(@NonNull View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text_log_line);
        }
    }
}