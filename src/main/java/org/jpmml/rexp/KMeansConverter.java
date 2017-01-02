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

import org.dmg.pmml.Array;
import org.dmg.pmml.CompareFunction;
import org.dmg.pmml.ComparisonMeasure;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.SquaredEuclidean;
import org.dmg.pmml.clustering.Cluster;
import org.dmg.pmml.clustering.ClusteringField;
import org.dmg.pmml.clustering.ClusteringModel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.clustering.ClusteringModelUtil;

public class KMeansConverter extends ModelConverter<RGenericVector> {

	public KMeansConverter(RGenericVector kmeans){
		super(kmeans);
	}

	@Override
	public boolean isSupervised(){
		return false;
	}

	@Override
	public void encodeFeatures(RExpEncoder encoder){
		RGenericVector kmeans = getObject();

		RDoubleVector centers = (RDoubleVector)kmeans.getValue("centers");

		RStringVector columnNames = centers.dimnames(1);
		for(int i = 0; i < columnNames.size(); i++){
			String columnName = columnNames.getValue(i);

			encoder.append(FieldName.create(columnName), false);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector kmeans = getObject();

		RDoubleVector centers = (RDoubleVector)kmeans.getValue("centers");
		RIntegerVector size = (RIntegerVector)kmeans.getValue("size");

		RIntegerVector centersDim = centers.dim();

		int rows = centersDim.getValue(0);
		int columns = centersDim.getValue(1);

		List<Cluster> clusters = new ArrayList<>();

		RStringVector rowNames = centers.dimnames(0);
		for(int i = 0; i < rowNames.size(); i++){
			Array array = PMMLUtil.createRealArray(RExpUtil.getRow(centers.getValues(), rows, columns, i));

			Cluster cluster = new Cluster()
				.setName(rowNames.getValue(i))
				.setId(String.valueOf(i + 1))
				.setSize(size.getValue(i))
				.setArray(array);

			clusters.add(cluster);
		}

		List<Feature> features = schema.getFeatures();

		List<ClusteringField> clusteringFields = ClusteringModelUtil.createClusteringFields(features);

		ComparisonMeasure comparisonMeasure = new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE)
			.setCompareFunction(CompareFunction.ABS_DIFF)
			.setMeasure(new SquaredEuclidean());

		Output output = ClusteringModelUtil.createOutput(FieldName.create("cluster"), clusters);

		ClusteringModel clusteringModel = new ClusteringModel(MiningFunction.CLUSTERING, ClusteringModel.ModelClass.CENTER_BASED, rows, ModelUtil.createMiningSchema(schema), comparisonMeasure, clusteringFields, clusters)
			.setOutput(output);

		return clusteringModel;
	}
}
