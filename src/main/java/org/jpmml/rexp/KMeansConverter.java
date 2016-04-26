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
import org.dmg.pmml.Cluster;
import org.dmg.pmml.ClusteringField;
import org.dmg.pmml.ClusteringModel;
import org.dmg.pmml.CompareFunctionType;
import org.dmg.pmml.ComparisonMeasure;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunctionType;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.SquaredEuclidean;
import org.jpmml.converter.ClusteringModelUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;

public class KMeansConverter extends Converter {

	@Override
	public PMML convert(RExp rexp){
		return convert((RGenericVector)rexp);
	}

	private PMML convert(RGenericVector kmeans){
		RDoubleVector centers = (RDoubleVector)kmeans.getValue("centers");
		RIntegerVector size = (RIntegerVector)kmeans.getValue("size");

		RIntegerVector dim = centers.dim();
		RGenericVector dimnames = (RGenericVector)centers.getAttributeValue("dimnames");

		int rows = dim.getValue(0);
		int columns = dim.getValue(1);

		List<DataField> dataFields = new ArrayList<>();

		RStringVector columnNames = (RStringVector)dimnames.getValue(1);
		for(int i = 0; i < columns; i++){
			String columnName = columnNames.getValue(i);

			DataField dataField = new DataField()
				.setName(FieldName.create(columnName))
				.setOpType(OpType.CONTINUOUS)
				.setDataType(DataType.DOUBLE);

			dataFields.add(dataField);
		}

		RStringVector rowNames = (RStringVector)dimnames.getValue(0);

		List<Cluster> clusters = new ArrayList<>();

		for(int i = 0; i < rows; i++){
			Array array = PMMLUtil.createRealArray(RExpUtil.getRow(centers.getValues(), rows, columns, i));

			Cluster cluster = new Cluster()
				.setName(rowNames.getValue(i))
				.setId(String.valueOf(i + 1))
				.setSize(size.getValue(i))
				.setArray(array);

			clusters.add(cluster);
		}

		List<FieldName> activeFields = PMMLUtil.getNames(dataFields);

		List<ClusteringField> clusteringFields = ClusteringModelUtil.createClusteringFields(activeFields);

		ComparisonMeasure comparisonMeasure = new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE)
			.setCompareFunction(CompareFunctionType.ABS_DIFF)
			.setMeasure(new SquaredEuclidean());

		MiningSchema miningSchema = ModelUtil.createMiningSchema(null, activeFields);

		Output output = ClusteringModelUtil.createOutput(FieldName.create("cluster"), clusters);

		ClusteringModel clusteringModel = new ClusteringModel(MiningFunctionType.CLUSTERING, ClusteringModel.ModelClass.CENTER_BASED, rows, miningSchema, comparisonMeasure, clusteringFields, clusters)
			.setOutput(output);

		DataDictionary dataDictionary = new DataDictionary(dataFields);

		PMML pmml = new PMML("4.2", createHeader(), dataDictionary)
			.addModels(clusteringModel);

		return pmml;
	}
}