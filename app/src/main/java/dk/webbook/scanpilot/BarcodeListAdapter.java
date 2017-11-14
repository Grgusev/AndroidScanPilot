package dk.webbook.scanpilot;

import android.content.Context;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Almond on 11/2/2017.
 */

public class BarcodeListAdapter extends BaseAdapter {

    ArrayList<String> barcodeList;
    ArrayList<Integer> timeList;
    ArrayList<Integer> rejectList;
    ArrayList<BarCodeCountDownTimer> counterList;
    boolean started     = true;
    Context mContext;

    public BarcodeListAdapter(Context context)
    {
        super();

        mContext = context;
        barcodeList = new ArrayList<>();
        timeList = new ArrayList<>();
        rejectList  = new ArrayList<>();
        counterList = new ArrayList<>();

        for (int i = 0; i < 4; i ++)
        {
            final int fInt  = i;
            counterList.add(new BarCodeCountDownTimer(i));
        }
        started = true;
    }

    public void addBarcode(String barcode, int rejected)
    {
        if (barcodeList.size() < 4)
        {
            barcodeList.add(barcode);
            timeList.add(0);
            rejectList.add(rejected);
            counterList.get(barcodeList.size() - 1).index = barcodeList.size() - 1;
            counterList.get(barcodeList.size() - 1).start();
        }
        else
        {
            started = false;
            for (int i = 0; i < barcodeList.size() - 1; i ++)
            {
                barcodeList.set(i, barcodeList.get(i + 1));
                timeList.set(i, timeList.get(i + 1));
                rejectList.set(i, rejectList.get(i + 1));
                counterList.get(i + 1).index = i;
            }

            barcodeList.set(3, barcode);
            timeList.set(3, 0);
            rejectList.set(3, rejected);
            counterList.get(0).index = 3;
            counterList.get(0).cancel();
            counterList.get(0).start();
        }

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return barcodeList.size();
    }

    @Override
    public Object getItem(int i) {
        return barcodeList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        TextView time, name, indicator;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.barcode_item_list, viewGroup, false);
        }

        time = (TextView)convertView.findViewById(R.id.timer_txt);
        name = (TextView)convertView.findViewById(R.id.barcode_txt);
        indicator = (TextView)convertView.findViewById(R.id.indicator_txt);

        setTimeText(i, time);
        setIndicator(i, indicator);
        name.setText(barcodeList.get(barcodeList.size() - 1 - i));

        return convertView;
    }

    void setIndicator(int index, TextView view)
    {
        if (view == null) return;

        if (index == rejectList.size() - 1 && started == true)
        {
            view.setBackgroundResource(R.drawable.indicator_round);
            return;
        }

        if (rejectList.get(rejectList.size() - 1 - index) == 1)
        {
            view.setBackgroundResource(R.drawable.indicator_round_green);
        }
        else if (rejectList.get(rejectList.size() - 1 - index) == 2)
        {
            view.setBackgroundResource(R.drawable.indicator_round_red);
        }
    }

    void setTimeText(int index, TextView view)
    {
        if (view == null) return;

        if (index == timeList.size() - 1 && started == true)
        {
            view.setText("Start");
            return;
        }
        view.setText(timeList.get(timeList.size() - 1 - index) + " sek");
    }

    public class BarCodeCountDownTimer extends CountDownTimer
    {
        public int index    = 0;

        public BarCodeCountDownTimer(int indexList)
        {
            super(3600000, 1000);
            index   = indexList;
        }

        @Override
        public void onTick(long millisUntilFinished) {
            if (timeList.size() > index)
            {
                timeList.set(index, timeList.get(index) + 1);
                notifyDataSetChanged();
            }
        }

        @Override
        public void onFinish() {
            this.start();
        }
    }
}
