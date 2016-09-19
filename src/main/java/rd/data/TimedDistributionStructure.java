package rd.data;

import java.io.File;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintain distribution (count) data between two variables V and K, can be used
 * for calculating joint probabilities - with time slicing to see how these
 * change
 * 
 * @author azahar
 *
 * @param <V>
 * @param <K>
 */
public class TimedDistributionStructure<V, K> {

	private static final Logger logger = LoggerFactory.getLogger(TimedDistributionStructure.class);
	private ConcurrentHashMap<V, Integer> rowIndex = new ConcurrentHashMap<>();
	private ConcurrentHashMap<K, Integer> colIndex = new ConcurrentHashMap<>();

	private final AtomicInteger currRow = new AtomicInteger(0), currCol = new AtomicInteger(0);
	private final AtomicInteger totalCount = new AtomicInteger(0);
	private final int[][][] count;
	private final int maxRow, maxCol, timesteps;
	private int currentTimeslice = 0;

	public TimedDistributionStructure(int _maxRow, int _maxCol, int _timesteps) {
		count = new int[_maxRow][_maxCol][_timesteps];
		maxRow = _maxRow;
		maxCol = _maxCol;
		timesteps = _timesteps;
	}

	public synchronized int nextTimeslice() {
		if (currentTimeslice < timesteps) {
			return ++currentTimeslice;
		} else {
			logger.error("Timeslice exceeds capacity.");
			return -1;
		}
	}

	public synchronized int getCurrentTimeslice() {

		return currentTimeslice;

	}

	public void add(V rowKey, K colKey) throws Exception {
		Integer rowI = rowIndex.get(rowKey);
		if (rowI == null) {
			rowI = currRow.getAndIncrement();
			rowIndex.put(rowKey, rowI);
		}

		Integer colI = colIndex.get(colKey);
		if (colI == null) {
			colI = currCol.getAndIncrement();
			colIndex.put(colKey, colI);
		}

		if (maxRow > rowI && maxCol > colI) {
			count[rowI][colI][currentTimeslice] += 1;
			totalCount.incrementAndGet();

		} else {
			throw new Exception("Space overrun, row or column index / time slice exceeds set bounds. Row Index: " + rowI
					+ "   Col Index: " + colI + ";  Max Row/Col: " + maxRow + " / " + maxCol);
		}

	}

	public int maxTimeslice() {
		return timesteps;
	}

	public Integer getCurrentRowCount() {
		return currRow.get();
	}

	public Integer getCurrentColumnCount() {
		return currCol.get();
	}

	/**
	 * 
	 * @param rowKey
	 * @param colKey
	 * @return A valid value >= 0 ; -1 on error
	 */
	public Integer get(V rowKey, K colKey) {
		Integer rowI = rowIndex.get(rowKey);
		Integer colI = colIndex.get(colKey);
		if (colI == null || rowI == null) {
			logger.error("Invalid index for row or column key: " + rowKey + "   " + colKey);
			return -1;
		}
		if (colI >= maxCol || rowI >= maxRow) {
			logger.error("Index larger than size for row or column: " + rowKey + "   " + colKey);
			return -1;
		}

		return count[rowI][colI][currentTimeslice];

	}

	public void writeToFile(File file, int timeslice) {

		try (PrintWriter pw = new PrintWriter(file)) {
			print(pw, timeslice);
			logger.info("Written timeslice to file: "+timeslice+ "  file: "+file);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);

		}

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("\n\t,");
		for (int ts = 0; ts < currentTimeslice; ts++) {
			sb.append("Time Slice: ");
			sb.append(ts);
			sb.append("\n");
			for (K colKey : colIndex.keySet()) {
				sb.append(colKey);
				sb.append(",\t");
			}
			sb.append("\n");

			for (V rowKey : rowIndex.keySet()) {
				Integer rowI = rowIndex.get(rowKey);
				sb.append(rowKey);
				sb.append(",\t");
				for (int i = 0; i < currCol.get(); i++) {
					sb.append(count[rowI][i]);
					sb.append(",\t");
				}
				sb.append("\n");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	public void print(PrintWriter w, int timeslice) {
		float sum = totalCount.get();
		if (sum <= 0) {
			sum = 1;
		}
		w.print("RowId");
		for (K colKey : colIndex.keySet()) {
			w.print(",");
			w.print(colKey);

		}
		w.println();

		for (V rowKey : rowIndex.keySet()) {

			Integer rowI = rowIndex.get(rowKey);
			w.print(rowKey);

			for (int i = 0; i < currCol.get(); i++) {

				w.print(",");

				w.print(count[rowI][i][timeslice]);

			}
			w.println();
		}
		w.println();

	}
}
