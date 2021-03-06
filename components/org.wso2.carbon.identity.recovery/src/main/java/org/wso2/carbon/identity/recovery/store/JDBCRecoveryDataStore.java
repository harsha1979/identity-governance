package org.wso2.carbon.identity.recovery.store;

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.application.common.model.User;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.IdentityRecoveryException;
import org.wso2.carbon.identity.recovery.model.UserRecoveryData;
import org.wso2.carbon.identity.recovery.util.Utils;

import java.sql.*;
import java.util.Date;

public class JDBCRecoveryDataStore implements UserRecoveryDataStore {
    @Override
    public void store(UserRecoveryData recoveryDataDO) throws IdentityRecoveryException {
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement prepStmt = null;
        try {
            prepStmt = connection.prepareStatement(IdentityRecoveryConstants.SQLQueries.STORE_RECOVERY_DATA);
            prepStmt.setString(1, recoveryDataDO.getUser().getUserName());
            prepStmt.setString(2, recoveryDataDO.getUser().getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(recoveryDataDO.getUser().getTenantDomain()));
            prepStmt.setString(4, recoveryDataDO.getSecret());
            prepStmt.setString(5, String.valueOf(recoveryDataDO.getRecoveryScenario()));
            prepStmt.setString(6, String.valueOf(recoveryDataDO.getRecoveryStep()));
            prepStmt.setTimestamp(7, new Timestamp(new Date().getTime()));
            prepStmt.setString(8, recoveryDataDO.getRemainingSetIds());
            prepStmt.execute();
            connection.setAutoCommit(false);
            connection.commit();
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_STORING_RECOVERY_DATA, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
            IdentityDatabaseUtil.closeConnection(connection);
        }
    }

    @Override
    public UserRecoveryData load(User user, Enum recoveryScenario, Enum recoveryStep, String code) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        ResultSet resultSet = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection();

        try {
            //TODO should have two sqls based on caseSenstitiveUsername
            String sql = IdentityRecoveryConstants.SQLQueries.LOAD_RECOVERY_DATA;

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain().toUpperCase());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));
            prepStmt.setString(4, code);
            prepStmt.setString(5, String.valueOf(recoveryScenario));
            prepStmt.setString(6, String.valueOf(recoveryStep));

            resultSet = prepStmt.executeQuery();

            if (resultSet.next()) {
                UserRecoveryData userRecoveryData = new UserRecoveryData(user, code, recoveryScenario, recoveryStep);
                if (StringUtils.isNotBlank(resultSet.getString("REMAINING_SETS"))) {
                    userRecoveryData.setRemainingSetIds(resultSet.getString("REMAINING_SETS"));
                }
                Timestamp timeCreated = resultSet.getTimestamp("TIME_CREATED");
                long createdTimeStamp = timeCreated.getTime();
                //TODO need to read from config
                int notificationExpiryTimeInMinutes = 3;
                long expiryTime = createdTimeStamp + notificationExpiryTimeInMinutes * 60 * 1000L;

                if (System.currentTimeMillis() > expiryTime) {
                    throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages
                            .ERROR_CODE_EXPIRED_CODE, code);
                }
                return userRecoveryData;
            }
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeResultSet(resultSet);
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }
        throw Utils.handleClientException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_INVALID_CODE, code);
    }

    @Override
    public void invalidate(String code) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        try {
            String sql = IdentityRecoveryConstants.SQLQueries.INVALIDATE_CODE;

            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, code);
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }
    }


    @Override
    public void invalidate(User user) throws IdentityRecoveryException {
        PreparedStatement prepStmt = null;
        Connection connection = IdentityDatabaseUtil.getDBConnection();
        try {
            String sql = IdentityRecoveryConstants.SQLQueries.INVALIDATE_USER_CODES;

            prepStmt = connection.prepareStatement(sql);
            //TODO need to do based on caseSensitiveUserName
            prepStmt.setString(1, user.getUserName());
            prepStmt.setString(2, user.getUserStoreDomain());
            prepStmt.setInt(3, IdentityTenantUtil.getTenantId(user.getTenantDomain()));
            prepStmt.execute();
            connection.commit();
        } catch (SQLException e) {
            throw Utils.handleServerException(IdentityRecoveryConstants.ErrorMessages.ERROR_CODE_UNEXPECTED, null, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }
    }
}
