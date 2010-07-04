package com.switkows.mileage;

import java.util.ArrayList;
import java.util.List;
import org.achartengine.chart.*;
import android.content.Context;
import android.graphics.Color;

public class PriceChart extends TimeChartExtension {
	//strings which will return series index
	public static final int 
		PRICE=0;
	private static final String title = "Price Per Gallon vs. Time";

	private static final String[] mTitles = {"Price"};
	private static final int[] colors = { Color.RED};
	private static final PointStyle[] styles = { PointStyle.SQUARE};

	public PriceChart(Context c, MileageData[] data) {
		super(c,title,"Price($)",mTitles,colors,styles,data);
	}

	@Override
	protected void appendDataToSeries(long date, float[] values) {
		appendDataToSeries(date,PRICE, values[0]);
	}


	@Override
	protected List<double[]> buildValuesList(MileageData[] data) {
		double[] price = new double[data.length];
		List<double[]> values = new ArrayList<double[]>();
		for (int i = 0; i < Titles.length; i++) {
			for(int row=0 ; row < data.length ; row++) {
				price[row] = data[row].getPrice();
			}
		}
		values.add(price);
		return values;
	}


	@Override
	protected List<double[]> buildXList(MileageData[] data) {
		List<double[]> x = new ArrayList<double[]>();
		for (int i = 0; i < Titles.length; i++) {
			double[] x_row = new double[data.length];
			for(int row=0 ; row < data.length ; row++) {
				x_row[row] = data[row].getDate();
			}
			x.add(x_row);
		}
		return x;
	}
}
