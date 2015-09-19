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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
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
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.SquaredEuclidean;
import org.jpmml.converter.PMMLUtil;

public class KMeansConverter extends Converter {

	@Override
	public PMML convert(RExp kmeans){
		RExp centers = RExpUtil.field(kmeans, "centers");
		RExp size = RExpUtil.field(kmeans, "size");

		RExp dim = RExpUtil.attribute(centers, "dim");
		RExp dimnames = RExpUtil.attribute(centers, "dimnames");

		int rows = dim.getIntValue(0);
		int columns = dim.getIntValue(1);

		ComparisonMeasure comparisonMeasure = new ComparisonMeasure(ComparisonMeasure.Kind.DISTANCE)
			.setMeasure(new SquaredEuclidean())
			.setCompareFunction(CompareFunctionType.ABS_DIFF);

		List<DataField> dataFields = new ArrayList<>();

		List<ClusteringField> clusteringFields = new ArrayList<>();

		RExp columnNames = dimnames.getRexpValue(1);
		for(int i = 0; i < columns; i++){
			RString name = columnNames.getStringValue(i);

			DataField dataField = new DataField()
				.setName(FieldName.create(name.getStrval()))
				.setOpType(OpType.CONTINUOUS)
				.setDataType(DataType.DOUBLE);

			dataFields.add(dataField);

			ClusteringField clusteringField = new ClusteringField(dataField.getName());

			clusteringFields.add(clusteringField);
		}

		List<Cluster> clusters = new ArrayList<>();

		RExp rowNames = dimnames.getRexpValue(0);
		for(int i = 0; i < rows; i++){
			RString name = rowNames.getStringValue(i);

			Array array = encodeArray(RExpUtil.getRow(centers.getRealValueList(), i, rows, columns));

			Cluster cluster = new Cluster()
				.setName(name.getStrval())
				.setId(String.valueOf(i + 1))
				.setSize(size.getIntValue(i))
				.setArray(array);

			clusters.add(cluster);
		}

		MiningSchema miningSchema = PMMLUtil.createMiningSchema(null, dataFields);

		Output output = encodeOutput(clusters);

		ClusteringModel clusteringModel = new ClusteringModel(MiningFunctionType.CLUSTERING, ClusteringModel.ModelClass.CENTER_BASED, rows, miningSchema, comparisonMeasure, clusteringFields, clusters)
			.setOutput(output);

		DataDictionary dataDictionary = new DataDictionary(dataFields);

		PMML pmml = new PMML("4.2", PMMLUtil.createHeader(Converter.NAME), dataDictionary)
			.addModels(clusteringModel);

		return pmml;
	}

	private Output encodeOutput(List<Cluster> clusters){
		Output output = new Output()
			.addOutputFields(PMMLUtil.createPredictedField(FieldName.create("cluster")));

		List<OutputField> outputFields = output.getOutputFields();
		outputFields.addAll(PMMLUtil.createAffinityFields(clusters));

		return output;
	}

	static
	private Array encodeArray(List<Double> values){
		Function<Double, String> function = new Function<Double, String>(){

			@Override
			public String apply(Double value){
				return PMMLUtil.formatValue(value);
			}
		};

		String value = PMMLUtil.formatArrayValue(Lists.transform(values, function));

		Array array = new Array(Array.Type.REAL, value);

		return array;
	}
}