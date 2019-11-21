/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.connect.hdfs.avro;

import io.confluent.connect.storage.hive.HiveConfig;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.kafka.connect.data.Schema;

import java.util.List;

import io.confluent.connect.avro.AvroData;
import io.confluent.connect.hdfs.HdfsSinkConnectorConfig;
import io.confluent.connect.hdfs.hive.HiveMetaStore;
import io.confluent.connect.hdfs.hive.HiveUtil;
import io.confluent.connect.hdfs.partitioner.Partitioner;
import io.confluent.connect.storage.common.StorageCommonConfig;
import io.confluent.connect.storage.errors.HiveMetaStoreException;
import io.confluent.connect.storage.hive.HiveSchemaConverter;

public class AvroHiveUtil extends HiveUtil {

  private static final String AVRO_SERDE = "org.apache.hadoop.hive.serde2.avro.AvroSerDe";
  private static final String AVRO_INPUT_FORMAT = "org.apache.hadoop.hive.ql.io.avro"
      + ".AvroContainerInputFormat";
  private static final String AVRO_OUTPUT_FORMAT = "org.apache.hadoop.hive.ql.io.avro"
      + ".AvroContainerOutputFormat";
  private static final String AVRO_SCHEMA_LITERAL = "avro.schema.literal";
  protected static final String AVRO_LOGICAL_TYPE_PROP = "logicalType";
  private final AvroData avroData;
  private final String topicsDir;
  private final boolean logicalTypeEnabled;

  public AvroHiveUtil(
      HdfsSinkConnectorConfig conf, AvroData avroData, HiveMetaStore
      hiveMetaStore
  ) {
    super(conf, hiveMetaStore);
    this.avroData = avroData;
    this.topicsDir = conf.getString(StorageCommonConfig.TOPICS_DIR_CONFIG);
    logicalTypeEnabled = conf.getBoolean(HiveConfig.HIVE_LOGICAL_TYPES_ENABLED_CONFIG);
  }

  @Override
  public void createTable(String database, String tableName, Schema schema, Partitioner partitioner)
      throws HiveMetaStoreException {
    Table table;
    table = constructAvroTable(database, tableName, schema, partitioner);
    hiveMetaStore.createTable(table);
  }

  @Override
  public void alterSchema(
      String database,
      String tableName,
      Schema schema
  ) throws HiveMetaStoreException {
    Table table = hiveMetaStore.getTable(database, tableName);
    List<FieldSchema> columns = logicalTypeEnabled
        ? HiveSchemaConverter.convertSchemaMaybeLogical(schema) :
        HiveSchemaConverter.convertSchema(schema);
    table.setFields(columns);
    hiveMetaStore.alterTable(table);
  }

  private Table constructAvroTable(
      String database,
      String tableName,
      Schema schema,
      Partitioner partitioner
  )
      throws HiveMetaStoreException {
    Table table = newTable(database, tableName);
    table.setTableType(TableType.EXTERNAL_TABLE);
    table.getParameters().put("EXTERNAL", "TRUE");
    String tablePath = hiveDirectoryName(url, topicsDir, tableName);
    table.setDataLocation(new Path(tablePath));
    table.setSerializationLib(AVRO_SERDE);
    try {
      table.setInputFormatClass(AVRO_INPUT_FORMAT);
      table.setOutputFormatClass(AVRO_OUTPUT_FORMAT);
    } catch (HiveException e) {
      throw new HiveMetaStoreException("Cannot find input/output format:", e);
    }
    // convert copycat schema schema to Hive columns
    List<FieldSchema> columns = logicalTypeEnabled
        ? HiveSchemaConverter.convertSchemaMaybeLogical(schema) :
        HiveSchemaConverter.convertSchema(schema);
    table.setFields(columns);
    table.setPartCols(partitioner.partitionFields());
    return table;
  }
}
