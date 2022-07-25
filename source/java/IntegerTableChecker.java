/*---------------------------------------------------------------
*  Copyright 2022 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.dic;

import java.awt.Color;
import javax.swing.*;
import java.io.*;
import java.util.*;
import org.rsna.util.FileUtil;

import org.apache.log4j.*;

import org.rsna.ui.ColorPane;
import org.rsna.util.JdbmUtil;
import jdbm.RecordManager;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;

/**
 * A Thread for checking/fixing the IntegerTable.
 */
public class IntegerTableChecker extends Thread {

	static final Logger logger = Logger.getLogger(IntegerTableChecker.class);
	
	ColorPane cp;
	boolean backup;
	JLabel sts;
	File data;
	boolean listAll;

	/**
	 * Class constructor.
	 */
    public IntegerTableChecker(ColorPane cp, boolean backup, JLabel sts, boolean listAll) {
		super();
		this.cp = cp;
		this.backup = backup;
		this.sts = sts;
		this.listAll = listAll;
	}
	
	public void run() {
		data = new File("data");
		try {
			//Original table to scan
			File dbOriginal = new File(data, "integers");
			RecordManager recmanOriginal = JdbmUtil.getRecordManager( dbOriginal.getAbsolutePath() );
			HTree tableOriginal = JdbmUtil.getHTree( recmanOriginal, "index" );
			
			//Backup table
			RecordManager recmanBackup = null;
			HTree tableBackup = null;
			//Create backup IntegerTable if selected
			if (backup) {
				//First delete the old backup
				new File(data, "integers-backup.db").delete();
				new File(data, "integers-backup.lg").delete();
				//Now create the new one
				File integerTableBackup = new File(data, "integers-filtered");
				recmanBackup = JdbmUtil.getRecordManager( integerTableBackup.getAbsolutePath() );
				tableBackup = JdbmUtil.getHTree( recmanBackup, "index" );
				if (tableBackup == null) {
					logger.warn("Unable to create the integer table backup at "+integerTableBackup);
					return;
				}
			}
			
			cp.println(Color.black, "Checking IntegerTable\n");

			//First, list the next keys
			FastIterator fit = tableOriginal.keys();
			Integer lastPtID = Integer.valueOf(0);
			try {
				cp.println("Checking special keys:");
				String key;
				while ( (key = (String)fit.next()) != null ) {
					Object value = tableOriginal.get(key);
					if (key.startsWith("__") && key.endsWith("__")) cp.println(key+" : "+value);
					if (key.equals("__\"ptid\"__")) lastPtID = (Integer)value;
				}
			}
			catch (Exception ex) {
				StringWriter sw = new StringWriter();
				ex.printStackTrace(new PrintWriter(sw));
				cp.println(Color.red, sw.toString());
			}
			cp.println("lastPtID = "+lastPtID);
			
			//Next, look for duplicate values
			cp.println("\nChecking for duplicate ptid values:");
			boolean ok = true;
			Hashtable<Integer,String> invtbl = new Hashtable<Integer,String>();
			fit = tableOriginal.keys();
			for (String key=(String)fit.next(); key!=null; key=(String)fit.next()) {
				if (key.startsWith("\"ptid\"")) {
					Integer i = (Integer)tableOriginal.get(key);
					String v = invtbl.get(i);
					if (v == null) invtbl.put(i, key);
					else {
						cp.println("Duplicate ptid value: "+key+" : "+ v + " ["+i+"]");
						ok = false;
					}
					if (i.intValue() > lastPtID.intValue()) {
						cp.println(key + " : " + i + " > "+lastPtID);
					}
				}
			}
			if (ok) cp.println("No duplicates found");
			
			//Now test all the keys
			cp.println("\nChecking all keys:");
			fit = tableOriginal.keys();
			int count = 0;
			int errorCount = 0;
			int nullCount = 0;
			String key = "notnull";
			while (key != null) {
				try {
					key = (String)fit.next();
					if (key != null) {
						Object value = tableOriginal.get(key);
						if (value != null) {
							count++;
							String text = count + ": " + key + ": " + value;
							if (listAll) cp.println(text);
							showSTS(text);
							if (recmanBackup != null) {
								tableBackup.put(key, value);
								if (count%40 == 0) recmanBackup.commit();
							}
						}
						else {
							nullCount++;
							cp.println("Null value for "+key);
						}
					}
				}
				catch (Throwable x) {
					errorCount++;
					String msg = null;
					if (x instanceof Exception) {
						msg = "Exception "+errorCount+" / "+count + ": "+x.getMessage();
					}
					else if (x instanceof Error) {
						msg = "Error "+errorCount+" / "+count + ": "+x.getMessage();
					}
					cp.println(msg);
					//System.out.println(msg);
					key = "notnull";
				}
			}
			if (recmanBackup != null) {
				recmanBackup.commit();
				recmanBackup.close();
			}
			recmanOriginal.commit();
			recmanOriginal.close();
			cp.println("");
			cp.println(Color.black, count + " non-null entries found and retrieved");
			cp.println(Color.black, nullCount + " null entries found");
			cp.println(Color.black, errorCount + " errors found");
			cp.println(Color.black, "\nFinished checking IntegerTable");
		}
		catch (Exception ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			cp.println(Color.red, sw.toString());
		}
	}
	
	void showSTS(String msg) {
		final String msgFinal = msg;
		final JLabel stsFinal = sts;
		Runnable r = new Runnable() {
			public void run() {
				stsFinal.setText(msgFinal);
			}
		};
		SwingUtilities.invokeLater(r);
	}
}