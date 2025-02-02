/*
 * The MIT License
 *
 * Copyright (c) 2004-2017 Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Peter Hayes, Tom Huybrechts, Daniel Beck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.microsoft.jenkins.azuread.utils;

import com.microsoft.jenkins.azuread.AzureAdUser;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.Util;
import hudson.model.User;
import hudson.security.SecurityRealm;
import hudson.security.UserMayOrMayNotExistException2;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.jenkinsci.plugins.matrixauth.AuthorizationType;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.jenkinsci.plugins.matrixauth.AuthorizationType.EITHER;
import static org.jenkinsci.plugins.matrixauth.AuthorizationType.GROUP;
import static org.jenkinsci.plugins.matrixauth.AuthorizationType.USER;

@Restricted(NoExternalUse.class)
public final class ValidationUtil {

    private static final int MAX_WIDTH = 50;

    private static final String USER_SYMBOL;
    private static final String GROUP_SYMBOL;
    private static final String WARNING_SYMBOL;
    private static final String ALERT_SYMBOL;

    private ValidationUtil() {
        // do not use
    }

    static {
        USER_SYMBOL = getSymbol("person", "icon-sm");
        GROUP_SYMBOL = getSymbol("people", "icon-sm");
        ALERT_SYMBOL = getSymbol("alert-circle", "icon-md mas-table__icon-alert");
        WARNING_SYMBOL = getSymbol("warning", "icon-md mas-table__icon-warning");
    }

    private static String getSymbol(String symbol, String classes) {
        SymbolRequest.Builder builder = new SymbolRequest.Builder();

        return Symbol.get(builder.withRaw("symbol-" + symbol + "-outline plugin-ionicons-api")
                .withClasses(classes)
                .build());
    }

    public static String formatNonExistentUserGroupValidationResponse(String user, String tooltip) {
        return formatNonExistentUserGroupValidationResponse(user, tooltip, false);
    }

   public static String formatNonExistentUserGroupValidationResponse(String user, String tooltip, boolean warning) {
        return formatUserGroupValidationResponse(
                "alert", "<span class='mas-table__cell--not-found'>" + user + "</span>", tooltip, warning);
    }

    public static String formatUserGroupValidationResponse(@NonNull AuthorizationType type, String user, String tooltip) {
        return formatUserGroupValidationResponse(type.toString(), user, tooltip, false);
    }

    public static String formatUserGroupValidationResponse(
            @NonNull AuthorizationType type, String user, String tooltip, boolean warning) {
        return formatUserGroupValidationResponse(type.toString(), user, tooltip, warning);
    }

    static String formatUserGroupValidationResponse(
            @NonNull String type, String user, String tooltip, boolean warning) {
        String symbol;
        switch (type) {
            case "GROUP":
                symbol = GROUP_SYMBOL;
                break;
            case "alert":
                symbol = ALERT_SYMBOL;
                break;
            case "USER":
                symbol = USER_SYMBOL;
                break;
            case "EITHER":
            default:
                symbol = "";
                break;
        }
        if (warning) {
            return String.format(
                    "<div tooltip='%s' class='mas-table__cell mas-table__cell-warning'>%s%s%s</div>",
                    tooltip, WARNING_SYMBOL, symbol, user);
        }
        return String.format("<div tooltip='%s' class='mas-table__cell'>%s%s</div>", tooltip, symbol, user);
    }

    public static FormValidation validateGroup(String groupName, SecurityRealm sr, boolean ambiguous) {
        String escapedSid = Functions.escape(groupName);
        try {
            sr.loadGroupByGroupname2(groupName, false);
            if (ambiguous) {
                return FormValidation.respond(
                        FormValidation.Kind.WARNING,
                        formatUserGroupValidationResponse(
                                GROUP,
                                escapedSid,
                                "Group found; but permissions would also be granted to a user of this name",
                                true));
            } else {
                return FormValidation.respond(
                        FormValidation.Kind.OK, formatUserGroupValidationResponse(GROUP, escapedSid, "Group"));
            }
        } catch (UserMayOrMayNotExistException2 e) {
            // undecidable, meaning the group may exist
            if (ambiguous) {
                return FormValidation.respond(
                        FormValidation.Kind.WARNING,
                        formatUserGroupValidationResponse(
                                GROUP,
                                escapedSid,
                                "Permissions would also be granted to a user or group of this name",
                                true));
            } else {
                return FormValidation.ok(escapedSid);
            }
        } catch (UsernameNotFoundException e) {
            // fall through next
        } catch (AuthenticationException e) {
            // other seemingly unexpected error.
            return FormValidation.error(e, "Failed to test the validity of the group name " + groupName);
        }
        return null;
    }

    public static FormValidation validateUser(String userName, SecurityRealm sr, boolean ambiguous) {
        String escapedSid = Functions.escape(userName);
        try {
            AzureAdUser userDetails = (AzureAdUser) sr.loadUserByUsername2(userName);
            User u = User.getById(userName, true);
            if (userName.equals(u.getFullName())) {
                // Sid and full name are identical, no need for tooltip
                if (ambiguous) {
                    return FormValidation.respond(
                            FormValidation.Kind.WARNING,
                            formatUserGroupValidationResponse(
                                    USER,
                                    userDetails.getUniqueName(),
                                    "User found; but permissions would also be granted to a group of this name",
                                    true));
                } else {
                    return FormValidation.respond(
                            FormValidation.Kind.OK, formatUserGroupValidationResponse(USER, userDetails.getUniqueName(), "User"));
                }
            }
            if (ambiguous) {
                return FormValidation.respond(
                        FormValidation.Kind.WARNING,
                        formatUserGroupValidationResponse(
                                USER,
                                Util.escape(StringUtils.abbreviate(u.getFullName(), MAX_WIDTH)),
                                "User " + escapedSid
                                        + " found; but permissions would also be granted to a group of this name",
                                true));
            } else {
                return FormValidation.respond(
                        FormValidation.Kind.OK,
                        formatUserGroupValidationResponse(
                                USER, Util.escape(StringUtils.abbreviate(u.getFullName(), MAX_WIDTH)), "User " + escapedSid));
            }
        } catch (UserMayOrMayNotExistException2 e) {
            // undecidable, meaning the user may exist
            if (ambiguous) {
                return FormValidation.respond(
                        FormValidation.Kind.WARNING,
                        formatUserGroupValidationResponse(
                                EITHER,
                                escapedSid,
                                "Permissions would also be granted to a user or group of this name",
                                true));
            } else {
                return FormValidation.ok(userName);
            }
        } catch (UsernameNotFoundException e) {
            // fall through next
        } catch (AuthenticationException e) {
            // other seemingly unexpected error.
            return FormValidation.error(e, "Failed to test the validity of the user ID " + userName);
        }
        return null;
    }
}
