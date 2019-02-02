/*
 * Copyright (c) 2014 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.CompareFunction;
import org.dmg.pmml.ComparisonMeasure;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.SquaredEuclidean;
import org.dmg.pmml.clustering.Cluster;
import org.dmg.pmml.clustering.ClusteringModel;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.clustering.ClusteringModelUtil;

public class KMeansConverter extends ModelConverter<RGenericVector> {

	public KMeansConverter(RGenericVector kmeans){
		super(kmeans);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector kmeans = getObject();

		RDoubleVector centers = kmeans.getDoubleValue("centers");

		RStringVector columnNames = centers.dimnames(1);
		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(columnName), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector kmeans = getObject();

		RDoubleVector centers = kmeans.getDoubleValue("centers");
		RIntegerVector size = kmeans.getIntegerValue("size");

		RIntegerVector centersDim = centers.dim();

		int rows = centersDim.getValue(0);
		int columns = centersDim.getValue(1);

		List<Cluster> clusters = new ArrayList<>();

		RStringVector rowNames = centers.dimnames(0);
		for(int i = 0; i < rowNames.size(); i++){
			Cluster cluster = new Cluster()
				.setId(String.valueOf(i + 1))
				.setName(rowNames.getValue(i))
				.setSize(size.getValue(i))
				.setArray(PMMLUtil.createRealArray(FortranMatrixUtil.getRow(centers.getValues(), rows, columns, i)));

			clusters.add(cluster);
		}

		ComparisonMeasure comparisonMeasure = new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE)
			.setCompareFunction(CompareFunction.ABS_DIFF)
			.setMeasure(new SquaredEuclidean());

		ClusteringModel clusteringModel = new ClusteringModel(MiningFunction.CLUSTERING, ClusteringModel.ModelClass.CENTER_BASED, rows, ModelUtil.createMiningSchema(schema.getLabel()), comparisonMeasure, ClusteringModelUtil.createClusteringFields(schema.getFeatures()), clusters)
			.setOutput(ClusteringModelUtil.createOutput(FieldName.create("cluster"), DataType.DOUBLE, clusters));

		return clusteringModel;
	}
}
