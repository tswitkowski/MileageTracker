package com.switkows.mileage;

import org.achartengine.GraphicalView;
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;

/**
 * This class holds all charts that will be
 * rentered for this application.
 * @author switkows
 *
 */
public class MileageChartManager extends DataSetObserver {
	private Context mContext;
	private Cursor mCursor;
	private MileageData[] dataSet;

	private TimeChartExtension[] charts;
	private static final int
			MPG_CHART = 0,
			MPG_DIFF_CHART = 1;
	
	public MileageChartManager(Context c, Cursor cursor) {
		mContext = c;
		mCursor = cursor;
		loadData();
		createCharts();
		mCursor.registerDataSetObserver(this);
	}
	
	private void loadData() {
		dataSet = new MileageData[mCursor.getCount()];
		for(int i=0 ; i<mCursor.getCount() ; i++) {
			mCursor.moveToPosition(i);
			dataSet[i] = new MileageData(mContext,mCursor);
			
		}
	}
	//FIXME - this should not go in the chartManager, but it's conventient..
	//		  should be moved to the content provider, instead
	public float getAverageMPG() {
		if(dataSet==null || dataSet.length==0)
			return 0;
		return getTotal(MileageData.TRIP)/getTotal(MileageData.GALLONS);
	}
	
	public float getAverageTrip() {
		return getAverage(MileageData.TRIP);
	}
	
	public float getAverageDiff() {
		return getAverage(MileageData.MPG_DIFF);
	}
	
	public float getBestMPG() {
		return getBest(MileageData.ACTUAL_MILEAGE);
	}
	
	public float getBestTrip() {
		return getBest(MileageData.TRIP);
	}
	
	public float getTotal(int field) {
		float total = 0;
		for(int i=0 ; i<dataSet.length ; i++)
			total += dataSet[i].getFloatValue(field);
		return total;
	}
	
	public float getAverage(int field) {
		if(dataSet==null || dataSet.length==0)
			return 0;
		return getTotal(field)/dataSet.length;
	}
	
	public float getBest(int field) {
		float best = 0;
		float curVal;
		for(int i=0 ; i<dataSet.length ; i++) {
			curVal = dataSet[i].getFloatValue(field);
			if( curVal > best)
				best = curVal;
		}
		return best;
	}
	
	
	private void createCharts() {
		charts = new TimeChartExtension[2];
		charts[MPG_CHART]      = new MileageChart(    mContext, dataSet);
		charts[MPG_DIFF_CHART] = new MileageDiffChart(mContext, dataSet);
	}
	
	private TimeChartExtension getChartStruct(int idx) {
		return charts[idx];
	}
	
	private GraphicalView getChartView(int idx) {
		return charts[idx].getChart();
	}
	public GraphicalView getMileageChart() {
		return getChartView(MPG_CHART);
	}
	
	public GraphicalView getDiffChart() {
		return getChartView(MPG_DIFF_CHART);
	}
	
	public void appendData(MileageData data, boolean autofit) {
		long date = data.getDate();
		//passing false as 3rd argument because we will simply iterate over all charts in autoFitCharts
		getChartStruct(MPG_CHART).appendDataToSeries(date, new float[] {data.getComputerMileage(), data.getActualMileage()}, false);
		getChartStruct(MPG_DIFF_CHART).appendDataToSeries(date, new float[] {data.getMileageDiff()}, false);
		if(autofit)
			autoFitCharts();
	}
	
	public void clearData(boolean repaint) {
		for(TimeChartExtension chart : charts)
			chart.clearData();
		if(repaint)
			updateCharts();
	}
	
	public void autoFitCharts() {
		for(TimeChartExtension chart : charts)
			chart.autoFitChart();
		updateCharts();
	}
	
	public void updateCharts() {
		for(TimeChartExtension chart : charts)
			chart.getChart().repaint();
	}
	
	public void onChanged() {
		loadData();
		createCharts();
	}

}
